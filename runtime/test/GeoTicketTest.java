package com.on.ebx.layout.runtime;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The geocoding ticket (audit L2-22): one this server issued is accepted; a forged signature, a payload changed under a
 * good signature (longer expiry, other user), an expired ticket signed with the real key, and malformed text are refused.
 */
public final class GeoTicketTest
{
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		final String good = GeoTicket.issue("alice");
		GeoTicketTest.check("issued ticket accepted", GeoTicket.valid(good), true);
		GeoTicketTest.check("issued for a user with dots and pipes", GeoTicket.valid(GeoTicket.issue("a.b|c")), true);

		final int last = good.lastIndexOf('.');
		final String payload = good.substring(0, last);
		GeoTicketTest.check("forged signature refused", GeoTicket.valid(payload + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), false);
		GeoTicketTest.check("empty signature refused", GeoTicket.valid(payload + "."), false);
		GeoTicketTest.check("last characters altered refused", GeoTicket.valid(good.substring(0, good.length() - 2) + "xx"), false);

		final String user = Base64.getUrlEncoder().withoutPadding().encodeToString("mallory".getBytes(StandardCharsets.UTF_8));
		final String[] parts = payload.split("\\.");
		GeoTicketTest.check("other user under the same signature refused", GeoTicket.valid(user + "." + parts[1] + "." + good.substring(last + 1)), false);
		GeoTicketTest.check("longer expiry under the same signature refused",
			GeoTicket.valid(parts[0] + "." + (Long.parseLong(parts[1]) + 86_400_000L) + "." + good.substring(last + 1)), false);

		// an expired ticket carrying a REAL signature: only the expiry may refuse it
		final Method sign = GeoTicket.class.getDeclaredMethod("sign", String.class);
		sign.setAccessible(true);
		final String old = parts[0] + "." + (System.currentTimeMillis() - 1000);
		GeoTicketTest.check("expired ticket refused", GeoTicket.valid(old + "." + sign.invoke(null, old)), false);
		final String fresh = parts[0] + "." + (System.currentTimeMillis() + 60_000);
		GeoTicketTest.check("same construction, not expired: accepted", GeoTicket.valid(fresh + "." + sign.invoke(null, fresh)), true);
		final String notANumber = parts[0] + ".soon";
		GeoTicketTest.check("expiry not a number refused", GeoTicket.valid(notANumber + "." + sign.invoke(null, notANumber)), false);

		GeoTicketTest.check("null refused", GeoTicket.valid(null), false);
		GeoTicketTest.check("empty refused", GeoTicket.valid(""), false);
		GeoTicketTest.check("no dot refused", GeoTicket.valid("abc"), false);
		GeoTicketTest.check("one dot refused", GeoTicket.valid("abc.def"), false);
		GeoTicketTest.check("dots only refused", GeoTicket.valid("..."), false);
		if (GeoTicketTest.failures > 0)
		{
			System.exit(1);
		}
	}

	private static void check(final String pWhat, final boolean pGot, final boolean pWant)
	{
		System.out.println((pGot == pWant ? "ok   " : "FAIL ") + pWhat);
		if (pGot != pWant)
		{
			GeoTicketTest.failures++;
		}
	}
}
