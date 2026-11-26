package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.service.Session;

/**
 * The user's own EBX session as a display condition (1.23.0), through documented APIs only: its tracking information
 * ({@code Session.getTrackingInfo}) and its attributes ({@code Session.getAttribute}), whatever set them in that session
 * (a workflow task, a trigger, a user service). Read when the form is drawn, like the roles: a value set later shows at
 * the next display.
 * <p>
 * A block, a field of a panel or a button of an "actions" block may carry {@code "session": {"trackingInfo": {"op",
 * "value"}, "attribute": {"name", "op", "value"}}}, {@code op} being {@code equals}, {@code contains},
 * {@code notEquals}, {@code notContains}, {@code filled} or {@code empty}; both parts must hold. Text is compared
 * trimmed and ignoring case; an attribute holding a collection matches when one of its values does.
 */
final class SessionState
{
	static final String[] OPS = { "equals", "contains", "notEquals", "notContains", "filled", "empty" };

	private SessionState()
	{
	}

	/** Whether the "session" conditions of a block, a field or a button let it show. No "session": always. */
	static boolean allows(final RenderContext rc, final JsonNode pSession)
	{
		return SessionState.allows(rc.context.getSession(), pSession);
	}

	/** The same test on a session, without a form. */
	static boolean allows(final Session session, final JsonNode pSession)
	{
		if (pSession == null || !pSession.isObject())
		{
			return true;
		}
		final JsonNode ti = pSession.get("trackingInfo");
		if (ti != null && !ti.path("op").asText("").isEmpty()
			&& !SessionState.test(ti.path("op").asText(), ti.path("value").asText(""), SessionState.values(session == null ? null : session.getTrackingInfo())))
		{
			return false;
		}
		final JsonNode at = pSession.get("attribute");
		final String name = at == null ? "" : at.path("name").asText("").trim();
		if (!name.isEmpty())
		{
			Object value;
			try
			{
				value = session == null ? null : session.getAttribute(name);
			}
			catch (final RuntimeException ex)
			{
				value = null;
			}
			return SessionState.test(at.path("op").asText("filled"), at.path("value").asText(""), SessionState.values(value));
		}
		return true;
	}

	/** The texts of a session value: none for null, each element of a collection or an array, else its text. */
	static List<String> values(final Object pValue)
	{
		final List<String> out = new ArrayList<String>();
		if (pValue instanceof Collection)
		{
			for (final Object o : (Collection<?>) pValue)
			{
				SessionState.add(o, out);
			}
		}
		else if (pValue instanceof Object[])
		{
			for (final Object o : (Object[]) pValue)
			{
				SessionState.add(o, out);
			}
		}
		else
		{
			SessionState.add(pValue, out);
		}
		return out;
	}

	private static void add(final Object pValue, final List<String> pOut)
	{
		final String s = pValue == null ? "" : String.valueOf(pValue).trim();
		if (!s.isEmpty())
		{
			pOut.add(s);
		}
	}

	/** One operator on the values found; an unknown operator lets the item show (the check reports it). */
	static boolean test(final String pOp, final String pExpected, final List<String> pValues)
	{
		final String want = pExpected == null ? "" : pExpected.trim().toLowerCase(Locale.ROOT);
		boolean equals = false;
		boolean contains = false;
		for (final String v : pValues)
		{
			final String got = v.toLowerCase(Locale.ROOT);
			equals |= got.equals(want);
			contains |= got.contains(want);
		}
		switch (pOp)
		{
			case "filled":
				return !pValues.isEmpty();
			case "empty":
				return pValues.isEmpty();
			case "equals":
				return want.isEmpty() ? pValues.isEmpty() : equals;
			case "notEquals":
				return want.isEmpty() ? !pValues.isEmpty() : !equals;
			case "contains":
				return !pValues.isEmpty() && contains;
			case "notContains":
				return pValues.isEmpty() || !contains;
			default:
				return true;
		}
	}
}
