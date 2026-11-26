package com.on.ebx.layout.runtime;

import java.util.Locale;

/**
 * What the module's geocoding address may do (1.15.1): the position of one address from the cache, else queued for the
 * background thread (one request a second, bounded queue); never a wait in the HTTP request (1.23.3).
 */
public final class Geocoding
{
	private Geocoding()
	{
	}

	/** What {@link #locateNow} answers. */
	public enum State
	{
		/** The position is in {@link Result#at}. */
		FOUND,
		/** Known, or the service is off: there is no position. */
		NOT_FOUND,
		/** Queued for the background thread: ask again in a couple of seconds. */
		PENDING,
		/** The queue is full: nothing was queued; try again later (HTTP 429). */
		REFUSED
	}

	public static final class Result
	{
		public final State state;
		/** {latitude, longitude} when FOUND. */
		public final double[] at;

		Result(final State pState, final double[] pAt)
		{
			this.state = pState;
			this.at = pAt;
		}
	}

	/**
	 * The position of one address, without ever waiting for the service (L2-10): from the cache at once, else the address is
	 * queued for the single background thread (one request a second) and the answer is PENDING; the caller asks again later.
	 */
	public static Result locateNow(final String pAddress, final Locale pLocale)
	{
		final double[] cached = Geocoder.known(pAddress);
		if (cached != null)
		{
			return new Result(State.FOUND, cached);
		}
		if (Geocoder.isKnown(pAddress) || !Geocoder.enabled())
		{
			return new Result(State.NOT_FOUND, null);
		}
		switch (Geocoder.enqueue(pAddress, pLocale))
		{
			case REFUSED:
				return new Result(State.REFUSED, null);
			case OFF:
				return new Result(State.NOT_FOUND, null);
			default:
				// the background thread may have answered meanwhile
				final double[] now = Geocoder.known(pAddress);
				return now != null ? new Result(State.FOUND, now) : new Result(State.PENDING, null);
		}
	}

	/** Where a form sends the browser to locate an address. */
	public static String url()
	{
		return "/" + LayoutPaths.MODULE_NAME + "/geocode";
	}
}
