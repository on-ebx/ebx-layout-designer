package com.on.ebx.layout.runtime;

import java.util.Date;

/** The changes block ignores a difference of milliseconds alone, never one of a second or more. */
public final class SameSecondTest
{
	public static void main(final String[] a)
	{
		int fail = 0;
		final long t = 1_791_846_467_000L; // a whole second
		fail += SameSecondTest.check("ms only", MoreBlocks.sameSecond(new Date(t + 386), new Date(t)), true);
		fail += SameSecondTest.check("one second", MoreBlocks.sameSecond(new Date(t + 1000), new Date(t)), false);
		fail += SameSecondTest.check("across a second", MoreBlocks.sameSecond(new Date(t + 999), new Date(t + 1000)), false);
		fail += SameSecondTest.check("equal", MoreBlocks.sameSecond(new Date(t), new Date(t)), true);
		fail += SameSecondTest.check("not dates", MoreBlocks.sameSecond("a", "a"), false);
		fail += SameSecondTest.check("one null", MoreBlocks.sameSecond(null, new Date(t)), false);
		if (fail > 0)
		{
			System.exit(1);
		}
	}

	private static int check(final String pWhat, final boolean pGot, final boolean pWant)
	{
		System.out.println((pGot == pWant ? "ok   " : "FAIL ") + pWhat);
		return pGot == pWant ? 0 : 1;
	}
}
