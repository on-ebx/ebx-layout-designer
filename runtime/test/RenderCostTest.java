package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

/** What keeps a form display cheap (audit L2-03, 05, 06, 07, 08, 17, 20, 24): tools/test.sh, no server needed. */
public final class RenderCostTest
{
	static int fail = 0;

	static void check(final String what, final Object got, final Object want)
	{
		if (!String.valueOf(got).equals(String.valueOf(want)))
		{
			fail++;
			System.out.println("FAIL " + what + ": got " + got + " want " + want);
		}
		else
		{
			System.out.println("ok   " + what);
		}
	}

	public static void main(final String[] a) throws Exception
	{
		shared();
		bounded();
		stamped();
		concurrent();
		org();
		theme();
		assets();
		workflow();
		cards();
		System.out.println(fail == 0 ? "ALL OK" : fail + " FAILED");
		if (fail > 0)
		{
			System.exit(1);
		}
	}

	/** L2-05: three blocks and two tabs on the same association open it once. */
	static void shared()
	{
		final SharedRows<String> rows = new SharedRows<String>();
		final AtomicInteger opened = new AtomicInteger();
		final SharedRows.Reader<String> reader = new SharedRows.Reader<String>()
		{
			@Override
			public List<String> read(final int max)
			{
				opened.incrementAndGet();
				final List<String> out = new ArrayList<String>();
				for (int i = 0; i < Math.min(max, 100); i++)
				{
					out.add("r" + i);
				}
				return out;
			}
		};
		check("first block reads", rows.get("a", 60, reader).size(), 60);
		check("second block, same size: no new read", rows.get("a", 60, reader).size() + ":" + opened.get(), "60:1");
		check("third block, fewer rows: no new read", rows.get("a", 12, reader).size() + ":" + opened.get(), "12:1");
		check("a block that needs more reads again, once", rows.get("a", 5000, reader).size() + ":" + opened.get(), "100:2");
		check("the whole association is known: nothing more", rows.get("a", 200, reader).size() + ":" + rows.get("a", 5000, reader).size() + ":" + opened.get(), "100:100:2");
		check("complete rows are shared with the counter", rows.completeRows("a").size(), 100);
		check("another association is read", rows.get("b", 5, reader).size() + ":" + opened.get(), "5:3");
	}

	/** L2-05 and L2-06: counting stops at the limit, however many rows follow. */
	static void bounded()
	{
		final AtomicInteger pulled = new AtomicInteger();
		final Iterator<Integer> ten = new Iterator<Integer>()
		{
			int i;

			@Override
			public boolean hasNext()
			{
				return this.i < 10000;
			}

			@Override
			public Integer next()
			{
				pulled.incrementAndGet();
				return Integer.valueOf(this.i++);
			}
		};
		final Bounded<Integer> b = Bounded.count(ten, 1000, null, 3);
		check("10,000 rows, limit 1000: counted", b.count, 1000);
		check("more than the limit is said", b.text(), "1000+");
		check("at most limit + 1 rows pulled", pulled.get() <= 1001, true);
		check("samples", b.samples, "[0, 1, 2]");
		final Bounded<Integer> few = Bounded.count(new ArrayList<Integer>(java.util.Arrays.asList(1, 2, 3, 4)).iterator(), 1000, new java.util.function.Predicate<Integer>()
		{
			@Override
			public boolean test(final Integer x)
			{
				return x.intValue() % 2 == 0;
			}
		}, 5);
		check("hidden rows are not counted", few.text() + ":" + few.visited, "2:4");
		// a user who sees almost nothing still costs at most the limit
		final Bounded<Integer> blind = Bounded.count(new Iterator<Integer>()
		{
			int i;

			@Override
			public boolean hasNext()
			{
				return true;
			}

			@Override
			public Integer next()
			{
				return Integer.valueOf(this.i++);
			}
		}, 50, new java.util.function.Predicate<Integer>()
		{
			@Override
			public boolean test(final Integer x)
			{
				return false;
			}
		}, 3);
		check("nothing visible: still bounded", blind.visited + ":" + blind.more, "50:true");
	}

	/** L2-07, L2-24: valid until the stamp changes, one build at a time, bounded. */
	static void stamped()
	{
		final StampedCache<String> cache = new StampedCache<String>(2);
		final AtomicInteger loads = new AtomicInteger();
		final StampedCache.Loader<String> loader = new StampedCache.Loader<String>()
		{
			@Override
			public String load()
			{
				return "v" + loads.incrementAndGet();
			}
		};
		check("first read builds", cache.get("t", Long.valueOf(1), loader), "v1");
		check("same date: not read again", cache.get("t", Long.valueOf(1), loader) + ":" + loads.get(), "v1:1");
		check("modified table: rebuilt", cache.get("t", Long.valueOf(2), loader) + ":" + loads.get(), "v2:2");
		check("no date, never cached", cache.get("n", null, loader) + cache.get("n", null, loader), "v3v4");
		cache.get("x", Long.valueOf(1), loader);
		cache.get("y", Long.valueOf(1), loader);
		check("at most two keys kept", cache.size(), 2);
		final StampedCache<String> failing = new StampedCache<String>(2);
		failing.get("f", Long.valueOf(1), new StampedCache.Loader<String>()
		{
			@Override
			public String load()
			{
				return null;
			}
		});
		check("a failed read is not kept", failing.size(), 0);
	}

	/** L2-07: two displays at once after a modification read the table once; a stale index is served while one rebuilds. */
	static void concurrent() throws Exception
	{
		final StampedCache<String> cache = new StampedCache<String>(4);
		final AtomicInteger loads = new AtomicInteger();
		final CountDownLatch inside = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final StampedCache.Loader<String> slow = new StampedCache.Loader<String>()
		{
			@Override
			public String load()
			{
				final int n = loads.incrementAndGet();
				inside.countDown();
				try
				{
					release.await();
				}
				catch (final InterruptedException ex)
				{
					Thread.currentThread().interrupt();
				}
				return "built" + n;
			}
		};
		final String[] got = new String[2];
		final Thread t1 = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				got[0] = cache.get("org", Long.valueOf(7), slow);
			}
		});
		t1.start();
		inside.await();
		final Thread t2 = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				got[1] = cache.get("org", Long.valueOf(7), slow);
			}
		});
		t2.start();
		Thread.sleep(150);
		release.countDown();
		t1.join();
		t2.join();
		check("two concurrent displays: the table is read once", loads.get(), 1);
		check("both get the same index", got[0] + "," + got[1], "built1,built1");
		// stale entry + a rebuild running elsewhere: no waiting
		final CountDownLatch in2 = new CountDownLatch(1);
		final CountDownLatch rel2 = new CountDownLatch(1);
		final Thread t3 = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				cache.get("org", Long.valueOf(8), new StampedCache.Loader<String>()
				{
					@Override
					public String load()
					{
						in2.countDown();
						try
						{
							rel2.await();
						}
						catch (final InterruptedException ex)
						{
							Thread.currentThread().interrupt();
						}
						return "fresh";
					}
				});
			}
		});
		t3.start();
		in2.await();
		final long t0 = System.currentTimeMillis();
		final String stale = cache.get("org", Long.valueOf(8), slow);
		check("stale index served while one rebuilds", stale + ":" + (System.currentTimeMillis() - t0 < 1000), "built1:true");
		rel2.countDown();
		t3.join();
		check("then the fresh one", cache.get("org", Long.valueOf(8), slow), "fresh");
	}

	/** L2-07: a table larger than the limit gives a partial index, announced. */
	static void org()
	{
		final List<String[]> pairs = new ArrayList<String[]>();
		pairs.add(new String[] { "1", null });
		pairs.add(new String[] { "2", "1" });
		pairs.add(new String[] { "3", "3" });
		pairs.add(new String[] { "4", "1" });
		pairs.add(new String[] { "5", "2" });
		OrgChart.Index all = OrgChart.build(pairs.iterator(), 10);
		check("links kept", all.parentOf.size() + ":" + all.partial, "3:false");
		check("children of 1", all.kids.get("1"), "[2, 4]");
		OrgChart.Index cut = OrgChart.build(pairs.iterator(), 2);
		check("beyond the limit: partial", cut.parentOf.size() + ":" + cut.partial, "2:true");
	}

	/** L2-20: the perspective's colours become properties; anything that is not a hex colour is refused. */
	static void theme()
	{
		check("main colour", Css.themeVars("#112233", null), ".ldf{--ldf-main:#112233;}");
		check("both", Css.themeVars("#112233", "#445566"), ".ldf{--ldf-main:#112233;--ldf-focus:#445566;}");
		check("none: the ON EBX fallbacks apply", Css.themeVars(null, " "), "");
		check("not a colour: refused", Css.themeVars("red;}</style><script>", null), "");
		check("style sheet takes the property with the old value as fallback", Css.STYLE.contains(".ldf-btn.ldf-solid{background:var(--ldf-main,#7F17FF)"), true);
		check("no literal main colour left in a rule", Css.STYLE.replace("var(--ldf-main,#7F17FF)", "").contains("#7F17FF"), false);
	}

	/** L2-17: the style sheet and scripts of a form are written once, whatever the number of tabs. */
	static void assets()
	{
		final FormAssets f = new FormAssets();
		check("three tabs: first writes", f.first() + "," + f.first() + "," + f.first(), "true,false,false");
	}

	/** L2-08: a publication that does not declare a variable is not asked again for a minute. */
	static void workflow()
	{
		final long now = 1_000_000L;
		check("unknown pair is asked", WorkflowState.knownUndeclared("P1", "tiers", now), false);
		WorkflowState.rememberUndeclared("P1", "tiers", now);
		check("remembered", WorkflowState.knownUndeclared("P1", "tiers", now + 1000), true);
		check("other publication still asked", WorkflowState.knownUndeclared("P2", "tiers", now + 1000), false);
		check("asked again after a minute", WorkflowState.knownUndeclared("P1", "tiers", now + 61000), false);
	}

	/** L2-03: a field the user may not read is left out of a card (the display function is Values.display in the form). */
	static void cards() throws Exception
	{
		final ObjectMapper m = new ObjectMapper();
		final java.util.function.Function<String, String> display = new java.util.function.Function<String, String>()
		{
			@Override
			public String apply(final String p)
			{
				return "/iban".equals(p) ? "" : "value of " + p; // /iban is hidden by a permission: Values.display answers ""
			}
		};
		check("hidden field not in the card", LayoutPane.cardLine(m.readTree("{\"cardFields\":[\"/name\",\"/iban\",\"/city\"]}"), display), "value of /name · value of /city");
		check("only hidden fields: nothing", LayoutPane.cardLine(m.readTree("{\"cardFields\":[\"/iban\"]}"), display), "");
	}
}
