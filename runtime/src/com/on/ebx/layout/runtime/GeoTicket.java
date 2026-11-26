package com.on.ebx.layout.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A signed ticket a form gives the browser so that it may ask the module's geocoding address (1.15.1): that servlet does
 * not see the EBX session, so it checks this instead. The key is drawn at random when the server starts and lives in this
 * class, which the server's lib shares between the form (runtime) and the module's webapp; a ticket is valid for four
 * hours and only for geocoding.
 */
public final class GeoTicket
{
	private static final long TTL_MS = 4L * 60 * 60 * 1000;
	private static final byte[] KEY = new byte[32];

	static
	{
		new SecureRandom().nextBytes(GeoTicket.KEY);
	}

	private GeoTicket()
	{
	}

	public static String issue(final String pUser)
	{
		final String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(pUser).getBytes(StandardCharsets.UTF_8)) + "."
			+ (System.currentTimeMillis() + GeoTicket.TTL_MS);
		return payload + "." + GeoTicket.sign(payload);
	}

	/** True when the ticket was issued by this server and has not expired. */
	public static boolean valid(final String pTicket)
	{
		if (pTicket == null)
		{
			return false;
		}
		final int last = pTicket.lastIndexOf('.');
		final int first = pTicket.indexOf('.');
		if (last <= 0 || first == last)
		{
			return false;
		}
		final String payload = pTicket.substring(0, last);
		if (!MessageDigest.isEqual(GeoTicket.sign(payload).getBytes(StandardCharsets.US_ASCII), pTicket.substring(last + 1).getBytes(StandardCharsets.US_ASCII)))
		{
			return false;
		}
		try
		{
			return Long.parseLong(payload.substring(first + 1)) > System.currentTimeMillis();
		}
		catch (final NumberFormatException ex)
		{
			return false;
		}
	}

	private static String sign(final String pPayload)
	{
		try
		{
			final Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(GeoTicket.KEY, "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(pPayload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (final Exception ex)
		{
			throw new IllegalStateException(ex);
		}
	}
}
