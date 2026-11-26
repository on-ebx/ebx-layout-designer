package com.on.ebx.layout.runtime;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/** L2-15: the row of a (model, table) is searched once per modification date of the FormLayout table, not at every form display. */
public final class LayoutStoreCacheTest
{
	public static void main(final String[] a)
	{
		final AtomicInteger searches = new AtomicInteger();
		final StampedCache.Loader<String> loader = new StampedCache.Loader<String>()
		{
			@Override
			public String load()
			{
				searches.incrementAndGet();
				return "7";
			}
		};
		final Date t0 = new Date(1_791_846_467_000L);
		int fail = 0;
		LayoutStore.rowKey("m|/root/A", t0, loader);
		LayoutStore.rowKey("m|/root/A", t0, loader);
		fail += LayoutStoreCacheTest.check("two finds, table unchanged: one search", searches.get() == 1);
		LayoutStore.rowKey("m|/root/B", t0, loader);
		fail += LayoutStoreCacheTest.check("another table: its own search", searches.get() == 2);
		LayoutStore.rowKey("m|/root/A", new Date(t0.getTime() + 1), loader);
		fail += LayoutStoreCacheTest.check("table modified: searched again", searches.get() == 3);
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
