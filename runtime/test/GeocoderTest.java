package com.on.ebx.layout.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Positions are read back from the cache file; with the service off nothing is queued and nothing is called. */
public final class GeocoderTest
{
	public static void main(final String[] a) throws Exception
	{
		final Path f = Files.createTempFile("geocache", ".tsv");
		Files.write(f, "10 rue de la paix, paris\t48.8686\t2.3316\nbroken line\n".getBytes(StandardCharsets.UTF_8));
		System.setProperty("ebx.layout.geocoder.cache", f.toString());
		System.setProperty("ebx.layout.geocoder.url", "off");
		int fail = 0;
		final double[] at = Geocoder.known("10  Rue de la Paix, Paris");
		fail += GeocoderTest.check("read from the file", at != null && Math.abs(at[0] - 48.8686) < 1e-9 && Math.abs(at[1] - 2.3316) < 1e-9);
		fail += GeocoderTest.check("unknown address", Geocoder.known("nowhere") == null);
		Geocoder.lookLater("nowhere", null);
		Thread.sleep(200);
		fail += GeocoderTest.check("service off: nothing looked up", Geocoder.known("nowhere") == null && !Geocoder.isKnown("nowhere"));
		final String t = GeoTicket.issue("alice");
		fail += GeocoderTest.check("ticket valid", GeoTicket.valid(t));
		fail += GeocoderTest.check("ticket altered", !GeoTicket.valid(t.substring(0, t.length() - 2) + "xx"));
		fail += GeocoderTest.check("no ticket", !GeoTicket.valid(null) && !GeoTicket.valid("abc"));
		fail += GeocoderTest.check("forged expiry", !GeoTicket.valid(t.replaceFirst("\\.\\d+\\.", ".99999999999999.")));
		// legacy line was migrated: file rewritten with the hash, no address text (L2-11)
		final String migrated = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
		fail += GeocoderTest.check("legacy file rewritten by hash", !migrated.contains("paix") && migrated.contains(Geocoder.keyOf("10 rue de la paix, paris")) && !migrated.contains("broken"));
		fail += GeocoderTest.check("same key whatever the case and spaces", Geocoder.keyOf(" 10  RUE de la Paix, Paris ").equals(Geocoder.keyOf("10 rue de la paix, paris")));
		Files.deleteIfExists(f);
		fail += GeocoderTest.cacheByHash();
		fail += GeocoderTest.purge();
		fail += GeocoderTest.nonBlocking();
		if (fail > 0)
		{
			System.exit(1);
		}
	}

	private static int cacheByHash() throws Exception
	{
		final Path f = Files.createTempFile("geocache", ".tsv");
		Files.delete(f);
		System.setProperty("ebx.layout.geocoder.cache", f.toString());
		Geocoder.reset();
		Geocoder.save(Geocoder.keyOf("12 Avenue Secrete, Lyon"), new double[] { 45.75, 4.85 });
		final String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
		int fail = GeocoderTest.check("file holds no address text", !text.toLowerCase().contains("secrete") && !text.contains("Lyon"));
		boolean owner = true;
		try
		{
			owner = Files.getPosixFilePermissions(f).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
		}
		catch (final UnsupportedOperationException ex)
		{
			// no POSIX modes here
		}
		fail += GeocoderTest.check("file mode 600", owner);
		Geocoder.reset();
		final double[] at = Geocoder.known("12 avenue  secrete, LYON");
		fail += GeocoderTest.check("position found again from the hash", at != null && at[0] == 45.75 && at[1] == 4.85);
		Files.deleteIfExists(f);
		return fail;
	}

	private static int purge() throws Exception
	{
		final Path f = Files.createTempFile("geocache", ".tsv");
		final long old = System.currentTimeMillis() - 100L * 24 * 3600 * 1000;
		final String fresh = Geocoder.keyOf("fresh place");
		final String stale = Geocoder.keyOf("stale place");
		Files.write(f, (stale + "\t1.0\t2.0\t" + old + "\n" + fresh + "\t3.0\t4.0\t" + System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8));
		System.setProperty("ebx.layout.geocoder.cache", f.toString());
		System.clearProperty("ebx.layout.geocoder.cacheDays");
		Geocoder.reset();
		int fail = GeocoderTest.check("entry older than 90 days dropped", Geocoder.known("stale place") == null);
		fail += GeocoderTest.check("recent entry kept", Geocoder.known("fresh place") != null);
		final String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
		fail += GeocoderTest.check("file compacted", !text.contains(stale) && text.contains(fresh));
		System.setProperty("ebx.layout.geocoder.cacheDays", "1");
		fail += GeocoderTest.check("retention is adjustable", Geocoder.known("fresh place") != null);
		Files.write(f, (fresh + "\t3.0\t4.0\t" + (System.currentTimeMillis() - 2L * 24 * 3600 * 1000) + "\n").getBytes(StandardCharsets.UTF_8));
		Geocoder.reset();
		fail += GeocoderTest.check("1-day retention drops a 2-day entry", Geocoder.known("fresh place") == null);
		System.clearProperty("ebx.layout.geocoder.cacheDays");
		Files.deleteIfExists(f);
		return fail;
	}

	/** A slow local "Nominatim": 20 concurrent callers must come back at once, the queue must be bounded and served by one thread. */
	private static int nonBlocking() throws Exception
	{
		final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.Set<String> threads = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
		final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		final com.sun.net.httpserver.HttpServer srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		srv.createContext("/search", ex -> {
			calls.incrementAndGet();
			try
			{
				release.await(3, java.util.concurrent.TimeUnit.SECONDS);
			}
			catch (final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			final byte[] body = "[{\"lat\":\"1.5\",\"lon\":\"2.5\"}]".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
		});
		srv.start();
		System.setProperty("ebx.layout.geocoder.url", "http://127.0.0.1:" + srv.getAddress().getPort() + "/search");
		System.setProperty("ebx.layout.geocoder.cache", "off");
		Geocoder.reset();
		int fail = 0;
		try
		{
			final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
			final java.util.List<java.util.concurrent.Future<Long>> times = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
			final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
			for (int i = 0; i < 20; i++)
			{
				final int n = i;
				times.add(pool.submit(() -> {
					go.await();
					final long t0 = System.nanoTime();
					final Geocoding.Result r = Geocoding.locateNow("slow address " + n, java.util.Locale.ENGLISH);
					final long ms = (System.nanoTime() - t0) / 1000000L;
					return Long.valueOf(r.state == Geocoding.State.PENDING ? ms : 100000L + ms);
				}));
			}
			go.countDown();
			long worst = 0;
			for (final java.util.concurrent.Future<Long> t : times)
			{
				worst = Math.max(worst, t.get().longValue());
			}
			pool.shutdown();
			fail += GeocoderTest.check("20 concurrent calls: all PENDING, none over 50 ms (worst " + worst + " ms)", worst < 50);
			// bounded queue: the single worker is held by the slow server, so distinct addresses pile up until refused
			int refused = 0;
			for (int i = 0; i < 700; i++)
			{
				if (Geocoding.locateNow("pile " + i, java.util.Locale.ENGLISH).state == Geocoding.State.REFUSED)
				{
					refused++;
				}
			}
			fail += GeocoderTest.check("queue bounded: " + refused + " refused of 700", refused > 0 && refused < 700);
			release.countDown();
			Thread.sleep(1500);
			for (final Thread t : Thread.getAllStackTraces().keySet())
			{
				if (t.getName().equals("layout-designer-geocoder"))
				{
					threads.add(t.getName() + "#" + t.getId());
				}
			}
			fail += GeocoderTest.check("one geocoder thread only (" + threads + ")", threads.size() == 1);
			fail += GeocoderTest.check("service called by that thread, not by the callers", calls.get() >= 1 && calls.get() <= 3);
			boolean cached = false;
			for (int i = 0; i < 20 && !cached; i++)
			{
				final Geocoding.Result done = Geocoding.locateNow("slow address " + i, java.util.Locale.ENGLISH);
				cached = done.state == Geocoding.State.FOUND && done.at[0] == 1.5;
			}
			fail += GeocoderTest.check("answered from the cache afterwards", cached);
		}
		finally
		{
			release.countDown();
			srv.stop(0);
			System.setProperty("ebx.layout.geocoder.url", "off");
		}
		return fail;
	}

	private static int check(final String pWhat, final boolean pOk)
	{
		System.out.println((pOk ? "ok   " : "FAIL ") + pWhat);
		return pOk ? 0 : 1;
	}
}
