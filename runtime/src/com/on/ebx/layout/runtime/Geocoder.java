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

	/** {latitude, longitude}, or null when the address is unknown or the service cannot be reached. */
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
