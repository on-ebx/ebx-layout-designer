package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
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
 */
final class WorkflowState
{
	private WorkflowState()
	{
	}

	/** True when the form is shown inside a workflow task (this session or a parent one). */
	static boolean inTask(final RenderContext rc)
	{
		final Object known = rc.cache.get("wf.inTask");
		if (known != null)
		{
			return ((Boolean) known).booleanValue();
		}
		boolean in;
		try
		{
			in = rc.context.getSession().isInWorkflowInteraction(true);
		}
		catch (final RuntimeException ex)
		{
			in = false;
		}
		rc.cache.put("wf.inTask", Boolean.valueOf(in));
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
	@SuppressWarnings("unchecked")
	static Map<String, ProcessInstance> running(final RenderContext rc, final List<String> pVars, final String pPublication)
	{
		final String key = "wf.running|" + pVars + "|" + (pPublication == null ? "" : pPublication);
		final Object known = rc.cache.get(key);
		if (known != null)
		{
			return (Map<String, ProcessInstance>) known;
		}
		final Map<String, ProcessInstance> found = new LinkedHashMap<String, ProcessInstance>();
		rc.cache.put(key, found);
		final Adaptation me = rc.record;
		if (me == null || rc.creating || pVars.isEmpty())
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
			engine = WorkflowEngine.getFromRepository(me.getHome().getRepository(), rc.context.getSession());
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
			for (final String v : pVars)
			{
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
						// the variable is not declared in this publication, or the publication does not exist
					}
				}
			}
		}
		return found;
	}

	/** Whether the "wf" conditions of a block or a button let it show. No "wf": always. */
	static boolean allows(final RenderContext rc, final JsonNode pWf)
	{
		if (pWf == null || !pWf.isObject())
		{
			return true;
		}
		final String ctx = pWf.path("ctx").asText("");
		if ("outside".equals(ctx) && WorkflowState.inTask(rc) || "inside".equals(ctx) && !WorkflowState.inTask(rc))
		{
			return false;
		}
		final String running = pWf.path("running").asText("");
		if ("hide".equals(running) || "show".equals(running))
		{
			final boolean any = !WorkflowState.running(rc, WorkflowState.vars(pWf.path("vars").asText("")), pWf.path("pub").asText("")).isEmpty();
			return "hide".equals(running) ? !any : any;
		}
		return true;
	}
}
