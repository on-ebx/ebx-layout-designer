package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.workflow.ProcessInstance;
import com.orchestranetworks.workflow.ProcessInstanceKey;
import com.orchestranetworks.workflow.PublishedProcessKey;
import com.orchestranetworks.workflow.WorkflowEngine;

/**
 * The workflow side of a form (1.17.0), through documented APIs only: whether the form is open in a workflow task
 * ({@code Session.isInWorkflowInteraction}), and which workflow instances are running for the record (its XPath or key in
 * a data context variable, {@code WorkflowEngine.getProcessInstanceKeysForDataContextVariableIncludingHistory}). Both are
 * computed once per display.
 * <p>
 * A block, or a button of an "actions" block, may carry {@code "wf": {"ctx": "outside"|"inside", "running": "hide"|"show",
 * "pub": "<publication, empty: any>", "vars": "<variables holding the record>"}}: {@code ctx} shows it only outside or only
 * inside a workflow task; {@code running} hides it, or shows it only, while such an instance is running for the record.
 * <p>
 * Since 1.23.2 (audit L2-08) {@code pub} should always be given (the designer requires it): with it the engine is asked about
 * one publication; without it about every publication, but a publication that does not declare a variable is remembered for
 * a minute and not asked again.
 */
final class WorkflowState
{
	/** How long a publication that does not declare a variable is not asked for it again (audit L2-08). */
	private static final long UNDECLARED_MS = 60000L;
	/** "publication|variable" → when to ask again. A publication's variables only change when it is republished. */
	private static final Map<String, Long> UNDECLARED = new java.util.concurrent.ConcurrentHashMap<String, Long>();

	private WorkflowState()
	{
	}

	/** True while the publication is known not to declare this variable: the engine is not asked. */
	static boolean knownUndeclared(final String pPublication, final String pVariable, final long pNow)
	{
		final Long until = WorkflowState.UNDECLARED.get(pPublication + "|" + pVariable);
		if (until == null)
		{
			return false;
		}
		if (until.longValue() <= pNow)
		{
			WorkflowState.UNDECLARED.remove(pPublication + "|" + pVariable);
			return false;
		}
		return true;
	}

	static void rememberUndeclared(final String pPublication, final String pVariable, final long pNow)
	{
		if (WorkflowState.UNDECLARED.size() > 5000)
		{
			WorkflowState.UNDECLARED.clear();
		}
		WorkflowState.UNDECLARED.put(pPublication + "|" + pVariable, Long.valueOf(pNow + WorkflowState.UNDECLARED_MS));
	}

	/** True when the form is shown inside a workflow task (this session or a parent one). */
	static boolean inTask(final RenderContext rc)
	{
		return WorkflowState.inTask(rc.context.getSession(), rc.cache);
	}

	static boolean inTask(final Session pSession, final Map<String, Object> pCache)
	{
		final Object known = pCache.get("wf.inTask");
		if (known != null)
		{
			return ((Boolean) known).booleanValue();
		}
		boolean in;
		try
		{
			in = pSession.isInWorkflowInteraction(true);
		}
		catch (final RuntimeException ex)
		{
			in = false;
		}
		pCache.put("wf.inTask", Boolean.valueOf(in));
		return in;
	}

	/** The variables of a comma or space separated list. */
	static List<String> vars(final String pList)
	{
		final List<String> vars = new ArrayList<String>();
		for (final String v : String.valueOf(pList == null ? "" : pList).split("[,;\\s]+"))
		{
			if (!v.isEmpty())
			{
				vars.add(v);
			}
		}
		return vars;
	}

	/** The running (not completed) instances whose variable holds this record, in one publication or (empty) in all. */
	static Map<String, ProcessInstance> running(final RenderContext rc, final List<String> pVars, final String pPublication)
	{
		return WorkflowState.running(rc.context.getSession(), rc.record, rc.creating, rc.cache, pVars, pPublication);
	}

	@SuppressWarnings("unchecked")
	static Map<String, ProcessInstance> running(final Session pSession, final Adaptation pRecord, final boolean pCreating, final Map<String, Object> pCache,
		final List<String> pVars, final String pPublication)
	{
		final String key = "wf.running|" + pVars + "|" + (pPublication == null ? "" : pPublication);
		final Object known = pCache.get(key);
		if (known != null)
		{
			return (Map<String, ProcessInstance>) known;
		}
		final Map<String, ProcessInstance> found = new LinkedHashMap<String, ProcessInstance>();
		pCache.put(key, found);
		final Adaptation me = pRecord;
		if (me == null || pCreating || pVars.isEmpty())
		{
			return found;
		}
		final Set<String> values = new LinkedHashSet<String>();
		// the variable holds the record's XPath (written with single or double quotes) or its primary key
		final String xpath = me.toXPathExpression();
		final String table = me.getContainerTable().getTablePath().format();
		final String predicate = me.toXPathPredicateString();
		for (final String x : new String[] { xpath, table + "[" + predicate + "]" })
		{
			values.add(x);
			values.add(x.replace('"', '\''));
			values.add(x.replace('\'', '"'));
		}
		values.add(me.getOccurrencePrimaryKey().format());
		final WorkflowEngine engine;
		try
		{
			engine = WorkflowEngine.getFromRepository(me.getHome().getRepository(), pSession);
		}
		catch (final RuntimeException ex)
		{
			return found;
		}
		final List<PublishedProcessKey> pubs = new ArrayList<PublishedProcessKey>();
		if (pPublication != null && !pPublication.trim().isEmpty())
		{
			pubs.add(PublishedProcessKey.forName(pPublication.trim()));
		}
		else
		{
			pubs.addAll(engine.getPublishedKeys());
		}
		for (final PublishedProcessKey pk : pubs)
		{
			final String pubName = String.valueOf(pk.getName());
			for (final String v : pVars)
			{
				if (WorkflowState.knownUndeclared(pubName, v, System.currentTimeMillis()))
				{
					continue;
				}
				for (final String value : values)
				{
					try
					{
						for (final ProcessInstanceKey k : engine.getProcessInstanceKeysForDataContextVariableIncludingHistory(pk, v, value))
						{
							if (!found.containsKey(k.format()))
							{
								final ProcessInstance pi = engine.getProcessInstance(k);
								if (pi != null && !pi.isCompleted())
								{
									found.put(k.format(), pi);
								}
							}
						}
					}
					catch (final RuntimeException ex)
					{
						// the variable is not declared in this publication, or the publication does not exist: not asked again
						// for a minute, nor for the other shapes of the value
						WorkflowState.rememberUndeclared(pubName, v, System.currentTimeMillis());
						break;
					}
				}
			}
		}
		return found;
	}

	/** Whether the "wf" conditions of a block or a button let it show. No "wf": always. */
	static boolean allows(final RenderContext rc, final JsonNode pWf)
	{
		return WorkflowState.allows(rc.context.getSession(), rc.record, rc.creating, rc.cache, pWf);
	}

	/** The same test on a session and a record, without a form. */
	static boolean allows(final Session pSession, final Adaptation pRecord, final boolean pCreating, final Map<String, Object> pCache, final JsonNode pWf)
	{
		if (pWf == null || !pWf.isObject())
		{
			return true;
		}
		final String ctx = pWf.path("ctx").asText("");
		if ("outside".equals(ctx) && WorkflowState.inTask(pSession, pCache) || "inside".equals(ctx) && !WorkflowState.inTask(pSession, pCache))
		{
			return false;
		}
		final String running = pWf.path("running").asText("");
		if ("hide".equals(running) || "show".equals(running))
		{
			final boolean any = !WorkflowState.running(pSession, pRecord, pCreating, pCache, WorkflowState.vars(pWf.path("vars").asText("")), pWf.path("pub").asText("")).isEmpty();
			return "hide".equals(running) ? !any : any;
		}
		return true;
	}
}
