package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Counting that stops at a limit, for the blocks that show "how many" without reading every row (audit L2-05, L2-06). */
final class Bounded<T>
{
	/** Visible rows seen, at most the limit. */
	final int count;
	/** True when there were more rows than the limit: show "count+". */
	final boolean more;
	/** The first visible rows, up to the number asked. */
	final List<T> samples;
	/** How many rows were looked at (visible or not): never more than the limit plus one. */
	final int visited;

	private Bounded(final int pCount, final boolean pMore, final List<T> pSamples, final int pVisited)
	{
		this.count = pCount;
		this.more = pMore;
		this.samples = pSamples;
		this.visited = pVisited;
	}

	/**
	 * Walks at most {@code pLimit} + 1 rows of {@code pRows}, counting those {@code pVisible} accepts and keeping the first
	 * {@code pSamples}. A limit of rows looked at, not of rows accepted: a user who sees few rows still costs at most the limit.
	 */
	static <T> Bounded<T> count(final Iterator<T> pRows, final int pLimit, final Predicate<T> pVisible, final int pSamples)
	{
		int n = 0;
		int visited = 0;
		boolean more = false;
		final List<T> samples = new ArrayList<T>();
		while (pRows.hasNext())
		{
			final T row = pRows.next();
			if (row == null)
			{
				break;
			}
			if (visited >= pLimit)
			{
				more = true;
				break;
			}
			visited++;
			if (pVisible != null && !pVisible.test(row))
			{
				continue;
			}
			n++;
			if (samples.size() < pSamples)
			{
				samples.add(row);
			}
		}
		return new Bounded<T>(n, more, samples, visited);
	}

	/** "12" or "1000+". */
	String text()
	{
		return this.more ? this.count + "+" : String.valueOf(this.count);
	}
}
