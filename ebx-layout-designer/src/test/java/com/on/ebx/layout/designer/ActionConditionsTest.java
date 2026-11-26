package com.on.ebx.layout.designer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.on.ebx.layout.runtime.ActionsBlock;
import com.orchestranetworks.service.Session;

/**
 * L2-01 / L2-23, without a server: a button's conditions (cond, wf, session) are tested again before it is launched, and
 * the first task is looked for once, not in a loop. Run by tools/test.sh.
 */
public final class ActionConditionsTest
{
	private static final ObjectMapper JSON = new ObjectMapper();
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		final Session plain = ActionConditionsTest.session("MANUAL", "validation", false);

		ActionConditionsTest.check("no condition: allowed", ActionConditionsTest.allowed(plain, false, ActionConditionsTest.json("{}")));
		ActionConditionsTest.check("cond true on this record: allowed", ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"cond\":\"x\"}")));
		ActionConditionsTest.check("cond false on this record: refused", !ActionConditionsTest.allowed(plain, false, ActionConditionsTest.json("{\"cond\":\"x\"}")));
		ActionConditionsTest.check("session tracking info not met: refused",
			!ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"session\":{\"trackingInfo\":{\"op\":\"equals\",\"value\":\"IMPORT\"}}}")));
		ActionConditionsTest.check("session tracking info met: allowed",
			ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"session\":{\"trackingInfo\":{\"op\":\"equals\",\"value\":\"manual\"}}}")));
		ActionConditionsTest.check("session attribute not met: refused",
			!ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"session\":{\"attribute\":{\"name\":\"etape\",\"op\":\"equals\",\"value\":\"autre\"}}}")));
		ActionConditionsTest.check("wf inside a task, session outside one: refused",
			!ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"wf\":{\"ctx\":\"inside\"}}")));
		ActionConditionsTest.check("wf outside a task, session outside one: allowed",
			ActionConditionsTest.allowed(plain, true, ActionConditionsTest.json("{\"wf\":{\"ctx\":\"outside\"}}")));
		ActionConditionsTest.check("one failing test among three refuses",
			!ActionConditionsTest.allowed(plain, false, ActionConditionsTest.json("{\"cond\":\"x\",\"wf\":{\"ctx\":\"outside\"}}")));

		final long start = System.currentTimeMillis();
		final Object item = ActionUserService.firstWorkItem(new java.util.function.Supplier<com.orchestranetworks.workflow.ProcessInstance>()
		{
			@Override
			public com.orchestranetworks.workflow.ProcessInstance get()
			{
				return null;
			}
		}, ActionUserService.FIRST_TASK_DELAY_MS);
		final long took = System.currentTimeMillis() - start;
		ActionConditionsTest.check("no work item yet: the inbox, in under 1.5 s (took " + took + " ms)", item == null && took < 1500);

		if (ActionConditionsTest.failures > 0)
		{
			System.out.println(ActionConditionsTest.failures + " action check(s) failed");
			System.exit(1);
		}
		System.out.println("Action conditions checks passed");
	}

	/** The conditions as the launching service asks them; pCondHolds is what the record answers to the XPath condition. */
	private static boolean allowed(final Session pSession, final boolean pCondHolds, final JsonNode pItem)
	{
		return ActionsBlock.allowed(pSession, null, pItem, new java.util.function.Predicate<String>()
		{
			@Override
			public boolean test(final String pCond)
			{
				return pCondHolds;
			}
		});
	}

	private static JsonNode json(final String pText) throws Exception
	{
		return ActionConditionsTest.JSON.readTree(pText);
	}

	private static void check(final String pWhat, final boolean pOk)
	{
		System.out.println((pOk ? "ok   " : "FAIL ") + pWhat);
		if (!pOk)
		{
			ActionConditionsTest.failures++;
		}
	}

	private static Session session(final String pTracking, final String pAttribute, final boolean pInTask)
	{
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] { Session.class }, new InvocationHandler()
		{
			@Override
			public Object invoke(final Object p, final Method m, final Object[] a)
			{
				if ("getTrackingInfo".equals(m.getName()))
				{
					return pTracking;
				}
				if ("getAttribute".equals(m.getName()))
				{
					return pAttribute;
				}
				return "isInWorkflowInteraction".equals(m.getName()) ? Boolean.valueOf(pInTask) : null;
			}
		});
	}
}
