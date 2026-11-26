package com.on.ebx.layout.runtime;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.service.LoggingCategory;

/**
 * The position of a postal address, asked to a Nominatim search service (OpenStreetMap's by default; the system property
 * {@code ebx.layout.geocoder.url} names another one, a self-hosted Nominatim for instance, and {@code off} disables it).
 * Answers are kept in memory by address text, failures for ten minutes, and calls are spaced by more than one second as
 * the public service's usage policy asks.
 * <p>
 * Since 1.15.0 a form never waits for the service: {@link #known} answers from memory only and {@link #lookLater} queues
 * the address for a single background thread, so that the position is there at the next display. Positions found are
 * also written to a file ({@code ebx.layout.geocoder.cache}, by default {@code <ebx.home>/layout-designer-geocache.tsv})
 * and read back at the first use, so that a restart does not send every address to the service again.
 * <p>
 * Since 1.23.3 (audit L2-10, L2-11): nothing waits for the service inside an HTTP request ({@link #enqueue} answers at once,
 * one daemon thread serves a bounded queue at the service's pace); the cache is keyed by the SHA-256 of the normalised
 * address (no address text in memory or in the file, only the hash, the position and the date), entries older than
 * {@code ebx.layout.geocoder.cacheDays} days (default 90) are dropped when the file is read and when it is compacted, and
 * the file is created readable by its owner only.
 */
final class Geocoder
{
	private static final String PROPERTY = "ebx.layout.geocoder.url";
	private static final String DEFAULT_URL = "https://nominatim.openstreetmap.org/search";
	private static final long MISS_TTL_MS = 10 * 60 * 1000L;
	private static final long SPACING_MS = 1100L;
	private static final int CACHE_SIZE = 5000;
	private static final String DAYS_PROPERTY = "ebx.layout.geocoder.cacheDays";
	private static final int DEFAULT_DAYS = 90;

	private static final Map<String, Object[]> CACHE = new LinkedHashMap<String, Object[]>(256, 0.75f, true)
	{
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, Object[]> pEldest)
		{
			return this.size() > Geocoder.CACHE_SIZE;
		}
	};

	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static long lastCall;

	private static final String CACHE_PROPERTY = "ebx.layout.geocoder.cache";
	private static final int QUEUE_MAX = 500;
	private static final java.util.Set<String> QUEUED = new java.util.HashSet<String>();
	private static final java.util.concurrent.ThreadPoolExecutor WORKER = new java.util.concurrent.ThreadPoolExecutor(1, 1, 60L,
		java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>(Geocoder.QUEUE_MAX), r -> {
			final Thread t = new Thread(r, "layout-designer-geocoder");
			t.setDaemon(true);
			return t;
		}, new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
	private static boolean loaded;
	private static int appended;

	/** What {@link #enqueue} answers. */
	enum Queued
	{
		/** The address is in the queue (or already was): ask again later. */
		PENDING,
		/** The queue is full: refused, nothing was queued. */
		REFUSED,
		/** The service is off: there will be no answer. */
		OFF
	}

	static
	{
		Geocoder.WORKER.allowCoreThreadTimeOut(true);
	}

	private Geocoder()
	{
	}

	static boolean enabled()
	{
		return !"off".equalsIgnoreCase(System.getProperty(Geocoder.PROPERTY, "").trim());
	}

	/** The cache key of an address: the SHA-256 (hex) of its normalised text, so that the address itself is never kept (L2-11). */
	static String keyOf(final String pAddress)
	{
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		try
		{
			final byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(q.getBytes(StandardCharsets.UTF_8));
			final StringBuilder b = new StringBuilder(64);
			for (final byte x : d)
			{
				b.append(String.format("%02x", Integer.valueOf(x & 0xff)));
			}
			return b.toString();
		}
		catch (final java.security.NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	private static long maxAgeMs()
	{
		int days = Geocoder.DEFAULT_DAYS;
		try
		{
			days = Integer.parseInt(System.getProperty(Geocoder.DAYS_PROPERTY, "").trim());
		}
		catch (final NumberFormatException ex)
		{
			// not set or not a number: the default
		}
		return Math.max(1, days) * 24L * 60 * 60 * 1000;
	}

	/** A cache entry still worth answering from: a position younger than the retention, a miss younger than ten minutes. */
	private static boolean fresh(final Object[] pHit)
	{
		final long age = System.currentTimeMillis() - ((Long) pHit[1]).longValue();
		return pHit[0] != null ? age < Geocoder.maxAgeMs() : age < Geocoder.MISS_TTL_MS;
	}

	/** True when the answer for this address is in memory (found, or not found less than ten minutes ago): no call needed. */
	static boolean isKnown(final String pAddress)
	{
		Geocoder.load();
		final String q = Geocoder.keyOf(pAddress);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(q);
			return hit != null && Geocoder.fresh(hit);
		}
	}

	/** The position in memory (or in the file) for this address, without calling the service; null when not known yet or not found. */
	static double[] known(final String pAddress)
	{
		Geocoder.load();
		final String q = Geocoder.keyOf(pAddress);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(q);
			return hit == null || !Geocoder.fresh(hit) ? null : (double[]) hit[0];
		}
	}

	/** Tables over this many rows placed by their address get one line in the log (1.15.1). */
	static final int LARGE_TABLE = 1000;
	private static final java.util.Set<String> CHECKED = new java.util.HashSet<String>();

	/**
	 * Once per table and per server start: a WARN line when a map block places the rows of a table of more than
	 * {@link #LARGE_TABLE} rows by their address instead of latitude and longitude fields.
	 */
	static void warnIfLarge(final com.onwbp.adaptation.AdaptationTable pTable, final String pBlock)
	{
		if (pTable == null)
		{
			return;
		}
		final String key = pTable.getContainerAdaptation().getHome().getKey().getName() + "/" + pTable.getContainerAdaptation().getAdaptationName().getStringName()
			+ pTable.getTablePath().format();
		synchronized (Geocoder.CHECKED)
		{
			if (!Geocoder.CHECKED.add(key))
			{
				return;
			}
		}
		try
		{
			final com.onwbp.adaptation.RequestResult all = pTable.createRequestResult(null);
			final int n;
			try
			{
				n = all.getSize();
			}
			finally
			{
				all.close();
			}
			if (n > Geocoder.LARGE_TABLE)
			{
				LoggingCategory.getKernel().warn("[layout-designer] block " + pBlock + " places the rows of " + key + " (" + n
					+ " rows) by their address: each unknown address is sent to the geocoding service, one a second at most. Add latitude and longitude fields to the table and choose them in the block.");
			}
		}
		catch (final RuntimeException ex)
		{
			// the count is only advice
		}
	}

	/** Queues the address for the background thread, once; the form that asked does not wait. */
	static void lookLater(final String pAddress, final Locale pLocale)
	{
		Geocoder.enqueue(pAddress, pLocale);
	}

	/**
	 * Queues the address for the single background thread and answers at once, never waiting for the service (L2-10):
	 * PENDING when it is queued (or was), REFUSED when the queue is full, OFF when the service is off. An address already
	 * known is not queued and answers PENDING-free: callers read {@link #known} first.
	 */
	static Queued enqueue(final String pAddress, final Locale pLocale)
	{
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ");
		if (!Geocoder.enabled())
		{
			return Queued.OFF;
		}
		if (q.isEmpty() || Geocoder.isKnown(q))
		{
			return Queued.PENDING;
		}
		final String key = Geocoder.keyOf(q);
		synchronized (Geocoder.QUEUED)
		{
			if (Geocoder.QUEUED.contains(key))
			{
				return Queued.PENDING;
			}
			if (Geocoder.QUEUED.size() >= Geocoder.QUEUE_MAX)
			{
				return Queued.REFUSED;
			}
			Geocoder.QUEUED.add(key);
		}
		try
		{
			Geocoder.WORKER.execute(() -> {
				try
				{
					final double[] at = Geocoder.locate(q, pLocale);
					if (at != null)
					{
						Geocoder.save(key, at);
					}
				}
				finally
				{
					synchronized (Geocoder.QUEUED)
					{
						Geocoder.QUEUED.remove(key);
					}
				}
			});
		}
		catch (final java.util.concurrent.RejectedExecutionException ex)
		{
			synchronized (Geocoder.QUEUED)
			{
				Geocoder.QUEUED.remove(key);
			}
			return Queued.REFUSED;
		}
		return Queued.PENDING;
	}

	/** For tests: forgets the memory cache so that the file is read again. */
	static void reset()
	{
		synchronized (Geocoder.CACHE)
		{
			Geocoder.CACHE.clear();
			Geocoder.loaded = false;
			Geocoder.appended = 0;
		}
	}

	private static java.io.File file()
	{
		final String configured = System.getProperty(Geocoder.CACHE_PROPERTY, "").trim();
		if ("off".equalsIgnoreCase(configured))
		{
			return null;
		}
		if (!configured.isEmpty())
		{
			return new java.io.File(configured);
		}
		final String home = System.getProperty("ebx.home", "").trim();
		return home.isEmpty() ? null : new java.io.File(home, "layout-designer-geocache.tsv");
	}

	private static final java.util.regex.Pattern HASH = java.util.regex.Pattern.compile("[0-9a-f]{64}");

	/**
	 * Reads the positions found before (one line per address: SHA-256 of the address, tab, latitude, tab, longitude, tab,
	 * date in milliseconds). Lines of the older format (address text, then the two coordinates) are hashed and the file is
	 * rewritten; so is it when entries are older than the retention (L2-11).
	 */
	private static void load()
	{
		synchronized (Geocoder.CACHE)
		{
			if (Geocoder.loaded)
			{
				return;
			}
			Geocoder.loaded = true;
		}
		final java.io.File f = Geocoder.file();
		if (f == null || !f.isFile())
		{
			return;
		}
		try
		{
			int n = 0;
			boolean rewrite = false;
			final long now = System.currentTimeMillis();
			final long max = Geocoder.maxAgeMs();
			for (final String line : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				final String[] p = line.split("\t");
				if (p.length != 3 && p.length != 4)
				{
					rewrite = true;
					continue;
				}
				try
				{
					final double[] at = new double[] { Double.parseDouble(p[1]), Double.parseDouble(p[2]) };
					final boolean legacy = p.length == 3 || !Geocoder.HASH.matcher(p[0]).matches();
					final long when = p.length == 4 ? Long.parseLong(p[3]) : now;
					if (legacy)
					{
						rewrite = true;
					}
					if (now - when >= max)
					{
						rewrite = true;
						continue;
					}
					final String key = legacy ? Geocoder.keyOf(p[0]) : p[0];
					synchronized (Geocoder.CACHE)
					{
						Geocoder.CACHE.put(key, new Object[] { at, Long.valueOf(when) });
					}
					n++;
				}
				catch (final NumberFormatException ex)
				{
					rewrite = true;
				}
			}
			LoggingCategory.getKernel().info("[layout-designer] geocoder: " + n + " positions read from " + f);
			if (rewrite)
			{
				Geocoder.compact();
			}
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] geocoder cache not read (" + f + "): " + ex);
		}
	}

	/** Creates the file readable by its owner only (where the file system has such modes). */
	private static void secure(final java.nio.file.Path pPath) throws java.io.IOException
	{
		if (!java.nio.file.Files.exists(pPath))
		{
			try
			{
				java.nio.file.Files.createFile(pPath, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
			}
			catch (final UnsupportedOperationException ex)
			{
				java.nio.file.Files.createFile(pPath);
			}
		}
	}

	/** Rewrites the file from memory: the positions still worth keeping, at most {@link #CACHE_SIZE}, nothing older than the retention. */
	static void compact()
	{
		final java.io.File f = Geocoder.file();
		if (f == null)
		{
			return;
		}
		final StringBuilder b = new StringBuilder();
		synchronized (Geocoder.CACHE)
		{
			for (final Map.Entry<String, Object[]> e : Geocoder.CACHE.entrySet())
			{
				final Object[] v = e.getValue();
				if (v[0] != null && Geocoder.fresh(v))
				{
					final double[] at = (double[]) v[0];
					b.append(e.getKey()).append('\t').append(at[0]).append('\t').append(at[1]).append('\t').append(v[1]).append('\n');
				}
			}
		}
		synchronized (Geocoder.class)
		{
			try
			{
				final java.nio.file.Path tmp = new java.io.File(f.getPath() + ".tmp").toPath();
				java.nio.file.Files.deleteIfExists(tmp);
				Geocoder.secure(tmp);
				java.nio.file.Files.write(tmp, b.toString().getBytes(StandardCharsets.UTF_8));
				java.nio.file.Files.move(tmp, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
				Geocoder.appended = 0;
			}
			catch (final Exception ex)
			{
				LoggingCategory.getKernel().warn("[layout-designer] geocoder cache not compacted (" + f + "): " + ex);
			}
		}
	}

	/** Remembers a position under the hash {@link #keyOf} of its address, in memory and in the file. */
	static void save(final String pKey, final double[] pAt)
	{
		final long now = System.currentTimeMillis();
		synchronized (Geocoder.CACHE)
		{
			Geocoder.CACHE.put(pKey, new Object[] { pAt, Long.valueOf(now) });
		}
		final java.io.File f = Geocoder.file();
		if (f == null)
		{
			return;
		}
		final boolean compact;
		synchronized (Geocoder.class)
		{
			try
			{
				Geocoder.secure(f.toPath());
				java.nio.file.Files.write(f.toPath(), (pKey + "\t" + pAt[0] + "\t" + pAt[1] + "\t" + now + "\n").getBytes(StandardCharsets.UTF_8),
					java.nio.file.StandardOpenOption.APPEND);
			}
			catch (final Exception ex)
			{
				LoggingCategory.getKernel().warn("[layout-designer] geocoder cache not written (" + f + "): " + ex);
			}
			compact = ++Geocoder.appended >= Geocoder.CACHE_SIZE;
		}
		if (compact)
		{
			Geocoder.compact();
		}
	}

	/** {latitude, longitude}, or null when the address is unknown or the service cannot be reached. Calls the service: never from a form. */
	static double[] locate(final String pAddress, final Locale pLocale)
	{
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ");
		if (q.isEmpty() || !Geocoder.enabled())
		{
			return null;
		}
		final String key = Geocoder.keyOf(q);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(key);
			if (hit != null && Geocoder.fresh(hit))
			{
				return (double[]) hit[0];
			}
		}
		final double[] found = Geocoder.ask(q, pLocale);
		synchronized (Geocoder.CACHE)
		{
			Geocoder.CACHE.put(key, new Object[] { found, Long.valueOf(System.currentTimeMillis()) });
		}
		return found;
	}

	private static double[] ask(final String pQuery, final Locale pLocale)
	{
		final String base = System.getProperty(Geocoder.PROPERTY, "").trim();
		final String url = (base.isEmpty() ? Geocoder.DEFAULT_URL : base) + "?format=jsonv2&limit=1&q=" + URLEncoder.encode(pQuery, StandardCharsets.UTF_8);
		try
		{
			Geocoder.space();
			final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4))
				.header("User-Agent", "ON-EBX-Layout-Designer (record form map block)")
				.header("Accept-Language", pLocale == null ? "en" : pLocale.toLanguageTag()).GET().build();
			final HttpResponse<String> response = Geocoder.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				LoggingCategory.getKernel().warn("[layout-designer] geocoder answered HTTP " + response.statusCode());
				return null;
			}
			final JsonNode list = LayoutStore.json().readTree(response.body());
			if (!list.isArray() || list.size() == 0)
			{
				return null;
			}
			final double lat = Double.parseDouble(list.get(0).path("lat").asText());
			final double lon = Double.parseDouble(list.get(0).path("lon").asText());
			return new double[] { lat, lon };
		}
		catch (final InterruptedException ex)
		{
			Thread.currentThread().interrupt();
			return null;
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] geocoder unreachable: " + ex);
			return null;
		}
	}

	/** At most one call per 1.1 s across the server. */
	private static synchronized void space() throws InterruptedException
	{
		final long wait = Geocoder.lastCall + Geocoder.SPACING_MS - System.currentTimeMillis();
		if (wait > 0)
		{
			Thread.sleep(wait);
		}
		Geocoder.lastCall = System.currentTimeMillis();
	}
}
