package com.on.ebx.layout.runtime;

import java.util.Arrays;
import java.util.List;

/** Checks the operators of the session conditions (run by tools/package.sh). */
public final class SessionStateCheck
{
	public static void main(final String[] args)
	{
		final List<String> ti = SessionState.values(" WF_ACHAT|step=validation ");
		final List<String> none = SessionState.values(null);
		SessionStateCheck.expect(SessionState.test("equals", "wf_achat|STEP=validation", ti), "equals ignores case and spaces");
		SessionStateCheck.expect(!SessionState.test("equals", "WF_ACHAT", ti), "equals is the whole text");
		SessionStateCheck.expect(SessionState.test("contains", "achat", ti), "contains");
		SessionStateCheck.expect(!SessionState.test("contains", "achat", none), "nothing contains anything");
		SessionStateCheck.expect(SessionState.test("notContains", "import", ti) && SessionState.test("notContains", "achat", none), "notContains");
		SessionStateCheck.expect(!SessionState.test("notEquals", "wf_achat|step=validation", ti) && SessionState.test("notEquals", "x", none), "notEquals");
		SessionStateCheck.expect(SessionState.test("filled", "", ti) && !SessionState.test("filled", "", none), "filled");
		SessionStateCheck.expect(SessionState.test("empty", "", SessionState.values("  ")), "blank is empty");
		SessionStateCheck.expect(SessionState.test("equals", "b", SessionState.values(Arrays.asList("a", "B"))), "a list matches by one of its values");
		SessionStateCheck.expect(SessionState.test("equals", "42", SessionState.values(Integer.valueOf(42))), "an object through its text");
		SessionStateCheck.expect(SessionState.test("bogus", "x", none), "an unknown operator lets the item show");
		System.out.println("SessionStateCheck: all checks passed");
	}

	private static void expect(final boolean ok, final String what)
	{
		if (!ok)
		{
			throw new AssertionError(what);
		}
	}
}
