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
 */
final class Geocoder
{
	private static final String PROPERTY = "ebx.layout.geocoder.url";
	private static final String DEFAULT_URL = "https://nominatim.openstreetmap.org/search";
	private static final long MISS_TTL_MS = 10 * 60 * 1000L;
	private static final long SPACING_MS = 1100L;
	private static final int CACHE_SIZE = 5000;

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

	/** True when the answer for this address is in memory (found, or not found less than ten minutes ago): no call needed. */
	static boolean isKnown(final String pAddress)
	{
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(q);
			return hit != null && (hit[0] != null || System.currentTimeMillis() - ((Long) hit[1]).longValue() < Geocoder.MISS_TTL_MS);
		}
	}

	/** The position in memory (or in the file) for this address, without calling the service; null when not known yet or not found. */
	static double[] known(final String pAddress)
	{
		Geocoder.load();
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(q);
			return hit == null ? null : (double[]) hit[0];
		}
	}

	/** Queues the address for the background thread, once; the form that asked does not wait. */
	static void lookLater(final String pAddress, final Locale pLocale)
	{
		final String q = pAddress == null ? "" : pAddress.trim().replaceAll("\\s+", " ");
		if (q.isEmpty() || !Geocoder.enabled() || Geocoder.isKnown(q))
		{
			return;
		}
		final String key = q.toLowerCase(Locale.ROOT);
		synchronized (Geocoder.QUEUED)
		{
			if (!Geocoder.QUEUED.add(key))
			{
				return;
			}
		}
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

	/** Reads the positions found before (one line per address: address, tab, latitude, tab, longitude). */
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
			for (final String line : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				final String[] p = line.split("\t");
				if (p.length == 3)
				{
					try
					{
						final double[] at = new double[] { Double.parseDouble(p[1]), Double.parseDouble(p[2]) };
						synchronized (Geocoder.CACHE)
						{
							Geocoder.CACHE.put(p[0], new Object[] { at, Long.valueOf(System.currentTimeMillis()) });
						}
						n++;
					}
					catch (final NumberFormatException ex)
					{
						// a damaged line: skipped
					}
				}
			}
			LoggingCategory.getKernel().info("[layout-designer] geocoder: " + n + " positions read from " + f);
		}
		catch (final Exception ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] geocoder cache not read (" + f + "): " + ex);
		}
	}

	private static void save(final String pKey, final double[] pAt)
	{
		final java.io.File f = Geocoder.file();
		if (f == null || pKey.indexOf('\t') >= 0 || pKey.indexOf('\n') >= 0)
		{
			return;
		}
		synchronized (Geocoder.class)
		{
			try
			{
				java.nio.file.Files.write(f.toPath(), (pKey + "\t" + pAt[0] + "\t" + pAt[1] + "\n").getBytes(StandardCharsets.UTF_8),
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			}
			catch (final Exception ex)
			{
				LoggingCategory.getKernel().warn("[layout-designer] geocoder cache not written (" + f + "): " + ex);
			}
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
		final String key = q.toLowerCase(Locale.ROOT);
		synchronized (Geocoder.CACHE)
		{
			final Object[] hit = Geocoder.CACHE.get(key);
			if (hit != null && (hit[0] != null || System.currentTimeMillis() - ((Long) hit[1]).longValue() < Geocoder.MISS_TTL_MS))
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
