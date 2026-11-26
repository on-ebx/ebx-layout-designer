package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows of each association read during ONE form display, shared by every block and tab that asks for them (audit
 * L2-05): the association is opened once per key and display, at the largest number of rows any block needed. A second
 * block asking for fewer rows, or for the same, reads nothing more.
 */
final class SharedRows<T>
{
	/** What reads at most {@code pMax} rows. */
	interface Reader<T>
	{
		List<T> read(int pMax);
	}

	private static final class Held<T>
	{
		final List<T> rows;
		final boolean complete;

		Held(final List<T> pRows, final boolean pComplete)
		{
			this.rows = pRows;
			this.complete = pComplete;
		}
	}

	private final Map<String, Held<T>> held = new HashMap<String, Held<T>>();
	private int reads;

	List<T> get(final String pKey, final int pMax, final Reader<T> pReader)
	{
		Held<T> h = this.held.get(pKey);
		if (h == null || !h.complete && h.rows.size() < pMax)
		{
			final List<T> rows = pReader.read(pMax);
			this.reads++;
			h = new Held<T>(rows, rows.size() < pMax);
			this.held.put(pKey, h);
		}
		return new ArrayList<T>(h.rows.subList(0, Math.min(pMax, h.rows.size())));
	}

	/** The rows already read for this key when they are all of the association, else null. */
	List<T> completeRows(final String pKey)
	{
		final Held<T> h = this.held.get(pKey);
		return h != null && h.complete ? h.rows : null;
	}

	/** How many times a reader ran (a test's instrument and a DEBUG log line). */
	int reads()
	{
		return this.reads;
	}
}
