package com.on.ebx.layout.runtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A small cache of values that are expensive to build from a table (the organisation chart's key to parent index, a
 * behaviour's configuration table) and valid as long as that table's {@code getLastModificationDate()} is unchanged
 * (audit L2-07, L2-24). Three rules, so that a busy table cannot turn every form opening into a scan:
 * <ul>
 * <li>the stamp is compared with {@link Object#equals}; a null stamp is never cached;</li>
 * <li>only one thread rebuilds a given key: the others wait for it and use its result, or, when a stale value exists,
 * use that one at once instead of waiting (the next opening gets the fresh one);</li>
 * <li>at most {@code maxEntries} keys are kept, the oldest built first leaves.</li>
 * </ul>
 */
final class StampedCache<V>
{
	/** What builds a value. */
	interface Loader<V>
	{
		V load();
	}

	private static final class Entry<V>
	{
		final Object stamp;
		final V value;

		Entry(final Object pStamp, final V pValue)
		{
			this.stamp = pStamp;
			this.value = pValue;
		}
	}

	private final int maxEntries;
	private final Map<String, Entry<V>> entries = new LinkedHashMap<String, Entry<V>>();
	private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<String, ReentrantLock>();

	StampedCache(final int pMaxEntries)
	{
		this.maxEntries = Math.max(1, pMaxEntries);
	}

	/** The value of this key for this stamp: cached, else built by {@code pLoader} (once, whoever asks). */
	V get(final String pKey, final Object pStamp, final Loader<V> pLoader)
	{
		if (pStamp == null)
		{
			return pLoader.load();
		}
		Entry<V> e = this.entry(pKey);
		if (e != null && pStamp.equals(e.stamp))
		{
			return e.value;
		}
		final ReentrantLock lock = this.locks.computeIfAbsent(pKey, k -> new ReentrantLock());
		if (e != null)
		{
			// stale: a rebuild already running elsewhere is not waited for
			if (!lock.tryLock())
			{
				return e.value;
			}
		}
		else
		{
			lock.lock();
		}
		try
		{
			e = this.entry(pKey);
			if (e != null && pStamp.equals(e.stamp))
			{
				return e.value;
			}
			final V built = pLoader.load();
			if (built != null) // a failed read is not kept
			{
				this.put(pKey, new Entry<V>(pStamp, built));
			}
			return built;
		}
		finally
		{
			lock.unlock();
		}
	}

	int size()
	{
		synchronized (this.entries)
		{
			return this.entries.size();
		}
	}

	private Entry<V> entry(final String pKey)
	{
		synchronized (this.entries)
		{
			return this.entries.get(pKey);
		}
	}

	private void put(final String pKey, final Entry<V> pEntry)
	{
		synchronized (this.entries)
		{
			this.entries.remove(pKey);
			this.entries.put(pKey, pEntry);
			final Iterator<String> oldest = this.entries.keySet().iterator();
			while (this.entries.size() > this.maxEntries && oldest.hasNext())
			{
				final String k = oldest.next();
				if (!k.equals(pKey))
				{
					oldest.remove();
					this.locks.remove(k);
				}
			}
		}
	}
}
