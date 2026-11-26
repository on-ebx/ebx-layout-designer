package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks the inheritance of attributes along a family tree (run by tools/package.sh). */
public final class FamilyCheck
{
	public static void main(final String[] args)
	{
		final Map<String, List<String[]>> own = new LinkedHashMap<String, List<String[]>>();
		FamilyCheck.put(own, "WOMEN", "/origin", "true");
		FamilyCheck.put(own, "WOMEN", "/fit", "false");
		FamilyCheck.put(own, "W-DRESS", "/length", "true");
		FamilyCheck.put(own, "W-DRESS", "/fit", "true");
		final Map<String, String> parents = new HashMap<String, String>();
		parents.put("W-DRESS", "WOMEN");
		parents.put("W-MAXI", "W-DRESS");
		parents.put("LOOP-A", "LOOP-B");
		parents.put("LOOP-B", "LOOP-A");
		final Map<String, List<String[]>> out = Behaviors.inherit(own, parents);
		FamilyCheck.expect(FamilyCheck.has(out, "W-DRESS", "/origin", "true"), "W-DRESS inherits /origin from WOMEN");
		FamilyCheck.expect(FamilyCheck.has(out, "W-DRESS", "/fit", "true"), "the closest level decides mandatory");
		FamilyCheck.expect(FamilyCheck.has(out, "W-MAXI", "/length", "true") && FamilyCheck.has(out, "W-MAXI", "/origin", "true"), "two levels up");
		FamilyCheck.expect(!FamilyCheck.has(out, "WOMEN", "/length", "true") && !FamilyCheck.has(out, "WOMEN", "/length", "false"), "a parent does not get its children's attributes");
		FamilyCheck.expect(!out.containsKey("LOOP-A"), "a loop with no attribute ends");
		System.out.println("FamilyCheck: all checks passed");
	}

	private static void put(final Map<String, List<String[]>> m, final String f, final String a, final String mand)
	{
		m.computeIfAbsent(f, k -> new ArrayList<String[]>()).add(new String[] { a, mand });
	}

	private static boolean has(final Map<String, List<String[]>> m, final String f, final String a, final String mand)
	{
		for (final String[] x : m.getOrDefault(f, new ArrayList<String[]>()))
		{
			if (x[0].equals(a) && x[1].equals(mand))
			{
				return true;
			}
		}
		return false;
	}

	private static void expect(final boolean ok, final String what)
	{
		if (!ok)
		{
			throw new AssertionError(what);
		}
	}
}
