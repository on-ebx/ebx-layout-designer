package com.on.ebx.layout.runtime;

import java.util.Locale;

/**
 * What the module's geocoding address may do (1.15.1): the position of one address, now, from the cache or from the
 * service (at its pace, one request a second); a position found goes to the same cache as the background lookups.
 */
public final class Geocoding
{
	private Geocoding()
	{
	}

	/** {latitude, longitude}, or null when not found or when the service is off or unreachable. */
	public static double[] locateNow(final String pAddress, final Locale pLocale)
	{
		final double[] cached = Geocoder.known(pAddress);
		if (cached != null || Geocoder.isKnown(pAddress))
		{
			return cached;
		}
		final double[] at = Geocoder.locate(pAddress, pLocale);
		if (at != null)
		{
			Geocoder.save(pAddress.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT), at);
		}
		return at;
	}

	/** Where a form sends the browser to locate an address. */
	public static String url()
	{
		return "/" + LayoutPaths.MODULE_NAME + "/geocode";
	}
}
