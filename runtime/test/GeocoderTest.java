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
		Files.deleteIfExists(f);
		if (fail > 0)
		{
			System.exit(1);
		}
	}

	private static int check(final String pWhat, final boolean pOk)
	{
		System.out.println((pOk ? "ok   " : "FAIL ") + pWhat);
		return pOk ? 0 : 1;
	}
}
