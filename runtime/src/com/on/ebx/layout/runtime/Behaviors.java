package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Role;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * The dynamic behaviours of a layout, all through documented APIs: a block or a field shown only for some values of
 * another field ({@code showIf}), a mandatory indicator that follows a value ({@code mandatoryIf}), a field read only
 * for some values or profiles ({@code readOnlyIf}), and the fields of a panel driven by a configuration table
 * ({@code attributesBy}). The live part runs in the browser on {@code ebx_form_getValue} (the controlling field's
 * widget calls {@code ldfDyn} after each change, {@code UIAtomicWidget.setActionOnAfterValueChanged}); the first state
 * is computed on the server from the record. A behaviour changes what is shown, never what EBX validates: a value
 * that must be mandatory needs a rule in the model.
 */
final class Behaviors
{
	/** A condition: {"field": path, "in": [...]} | {"notIn": [...]} | {"filled": true} | {"empty": true}. */
	static boolean test(final JsonNode pCond, final Object pValue)
	{
		if (pCond == null || !pCond.isObject() || pCond.path("field").asText("").isEmpty())
		{
			return true;
		}
		final List<String> values = new ArrayList<String>();
		if (pValue instanceof List)
		{
			for (final Object o : (List<?>) pValue)
			{
				values.add(Values.key(o));
			}
		}
		else if (pValue != null)
		{
			values.add(Values.key(pValue));
		}
		if (pCond.path("filled").asBoolean(false))
		{
			return Values.filled(pValue);
		}
		if (pCond.path("empty").asBoolean(false))
		{
			return !Values.filled(pValue);
		}
		if (pCond.has("notIn"))
		{
			return !Behaviors.anyIn(values, pCond.get("notIn"));
		}
		if (pCond.has("in"))
		{
			return Behaviors.anyIn(values, pCond.get("in"));
		}
		return Values.filled(pValue);
	}

	private static boolean anyIn(final List<String> pValues, final JsonNode pList)
	{
		for (final String v : pValues)
		{
			for (final JsonNode x : pList)
			{
				if (x.asText().equalsIgnoreCase(v))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Every field whose changes must re-run the behaviours, in the whole layout. */
	static void controllers(final JsonNode pBlock, final Set<String> pOut)
	{
		Behaviors.addController(pBlock.get("showIf"), pOut);
		final String key = pBlock.path("attributesBy").path("key").asText("");
		if (!key.isEmpty())
		{
			pOut.add(key);
		}
		for (final JsonNode f : pBlock.path("fields"))
		{
			Behaviors.addController(f.get("showIf"), pOut);
			Behaviors.addController(f.get("mandatoryIf"), pOut);
		}
	}

	private static void addController(final JsonNode pCond, final Set<String> pOut)
	{
		if (pCond != null && !pCond.path("field").asText("").isEmpty())
		{
			pOut.add(pCond.path("field").asText());
		}
	}

	/** readOnlyIf: {"field", "in"…} and/or {"roles": [...]} (read only for these profiles) and {"exceptRoles": [...]}. */
	static boolean readOnly(final RenderContext pRc, final JsonNode pRule)
	{
		if (pRule == null || !pRule.isObject())
		{
			return false;
		}
		final Session session = pRc.context.getSession();
		if (pRule.path("exceptRoles").size() > 0 && Behaviors.inAnyRole(session, pRule.get("exceptRoles")))
		{
			return false;
		}
		final boolean byValue = !pRule.path("field").asText("").isEmpty();
		final boolean byRole = pRule.path("roles").size() > 0;
		if (!byValue && !byRole)
		{
			return pRule.path("exceptRoles").size() > 0;
		}
		return (!byValue || Behaviors.test(pRule, pRc.raw(pRule.path("field").asText()))) && (!byRole || Behaviors.inAnyRole(session, pRule.get("roles")));
	}

	static boolean inAnyRole(final Session pSession, final JsonNode pRoles)
	{
		if (pSession == null || pRoles == null)
		{
			return false;
		}
		for (final JsonNode r : pRoles)
		{
			final String name = r.asText("").trim();
			if (name.isEmpty())
			{
				continue;
			}
			try
			{
				final Profile p = "administrator".equalsIgnoreCase(name) || "Badministrator".equals(name) ? Role.ADMINISTRATOR : Role.forSpecificRole(name);
				if (pSession.isUserInRole(p))
				{
					return true;
				}
			}
			catch (final RuntimeException ex)
			{
				// an unknown profile name
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------------- rules for the browser

	/** The rules of one pane, written as JavaScript at the end of the pane. */
	static final class Rules
	{
		private final UIFormPaneWriter writer;
		private final ArrayNode rules = LayoutStore.json().createArrayNode();

		Rules(final UIFormPaneWriter pWriter)
		{
			this.writer = pWriter;
		}

		String prefixed(final String pStored)
		{
			try
			{
				return this.writer.getPrefixedPath(LayoutPaths.relative(pStored)).format();
			}
			catch (final RuntimeException ex)
			{
				return LayoutPaths.relative(pStored).format();
			}
		}

		private ObjectNode cond(final RenderContext pRc, final JsonNode pCond)
		{
			final ObjectNode c = ((ObjectNode) pCond.deepCopy());
			final String field = pCond.path("field").asText();
			c.put("c", this.prefixed(field));
			c.put("ct", Behaviors.controllerType(pRc.node(field)));
			return c;
		}

		void show(final RenderContext pRc, final JsonNode pCond, final String pDomId, final List<String> pClear)
		{
			final ObjectNode r = this.rules.addObject();
			r.put("k", "show");
			r.set("q", this.cond(pRc, pCond));
			r.put("id", pDomId);
			final ArrayNode clear = r.putArray("clear");
			if (pClear != null)
			{
				for (final String p : pClear)
				{
					clear.add(this.prefixed(p));
				}
			}
		}

		void mandatory(final RenderContext pRc, final JsonNode pCond, final String pStored)
		{
			final ObjectNode r = this.rules.addObject();
			r.put("k", "mand");
			r.set("q", this.cond(pRc, pCond));
			r.put("p", this.prefixed(pStored));
		}

		/** attributesBy: the panel's fields, shown only when the configuration table lists them for the current key. */
		void family(final RenderContext pRc, final JsonNode pBy, final String pPanelId, final List<String[]> pFields)
		{
			final Map<String, List<String[]>> map = Behaviors.familyMap(pRc, pBy);
			if (map == null)
			{
				return;
			}
			final ObjectNode r = this.rules.addObject();
			r.put("k", "fam");
			final String key = pBy.path("key").asText();
			r.put("c", this.prefixed(key));
			r.put("ct", Behaviors.controllerType(pRc.node(key)));
			r.put("id", pPanelId);
			r.put("hideEmpty", pBy.path("hideEmpty").asBoolean(true));
			final ObjectNode m = r.putObject("map");
			for (final Map.Entry<String, List<String[]>> e : map.entrySet())
			{
				final ArrayNode list = m.putArray(e.getKey());
				for (final String[] a : e.getValue())
				{
					list.addObject().put("a", a[0]).put("m", "true".equals(a[1]));
				}
			}
			final ArrayNode fields = r.putArray("fields");
			for (final String[] f : pFields)
			{
				fields.addObject().put("id", f[0]).put("a", f[1]).put("p", this.prefixed(f[1]));
			}
		}

		boolean isEmpty()
		{
			return this.rules.size() == 0;
		}

		String js()
		{
			return "(window.ldfRules=window.ldfRules||[]).push.apply(window.ldfRules," + this.rules.toString() + ");" + Behaviors.ENGINE + "setTimeout(window.ldfDyn,0);";
		}
	}

	/** What the browser receives from ebx_form_getValue for this kind of field (see the widgets' "JavaScript type"). */
	static String controllerType(final SchemaNode pNode)
	{
		if (pNode == null)
		{
			return "";
		}
		if (pNode.getMaxOccurs() > 1)
		{
			return "list";
		}
		return pNode.getOccurrencesJavaClass() == Boolean.class ? "bool" : "";
	}

	/** Configuration table → {key value: [[attribute path, "true" if mandatory]]}; null when it cannot be read. */
	static Map<String, List<String[]>> familyMap(final RenderContext pRc, final JsonNode pBy)
	{
		final String tablePath = pBy.path("table").asText("");
		final String familyCol = pBy.path("familyCol").asText("");
		final String attributeCol = pBy.path("attributeCol").asText("");
		if (tablePath.isEmpty() || familyCol.isEmpty() || attributeCol.isEmpty())
		{
			return null;
		}
		final String cacheKey = "fam|" + tablePath + "|" + familyCol + "|" + attributeCol + "|" + pBy.path("mandatoryCol").asText("");
		@SuppressWarnings("unchecked")
		final Map<String, List<String[]>> known = (Map<String, List<String[]>>) pRc.cache.get(cacheKey);
		if (known != null)
		{
			return known;
		}
		final Map<String, List<String[]>> map = new LinkedHashMap<String, List<String[]>>();
		try
		{
			final Adaptation dataset = pRc.context.getCurrentTable().getContainerAdaptation();
			final AdaptationTable table = dataset.getTable(Path.parse(tablePath));
			final String mandatoryCol = pBy.path("mandatoryCol").asText("");
			final RequestResult rows = table.createRequest().execute();
			try
			{
				for (Adaptation r = rows.nextAdaptation(); r != null; r = rows.nextAdaptation())
				{
					final String fam = Values.key(Values.raw(r, familyCol));
					final String attr = Behaviors.attributePath(pRc, Values.key(Values.raw(r, attributeCol)));
					if (fam.isEmpty() || attr == null)
					{
						continue;
					}
					final boolean mandatory = !mandatoryCol.isEmpty() && Boolean.TRUE.equals(Values.raw(r, mandatoryCol));
					List<String[]> list = map.get(fam);
					if (list == null)
					{
						list = new ArrayList<String[]>();
						map.put(fam, list);
					}
					list.add(new String[] { attr, String.valueOf(mandatory) });
				}
			}
			finally
			{
				rows.close();
			}
		}
		catch (final RuntimeException ex)
		{
			LoggingCategory.getKernel().warn("[layout-designer] configuration table " + tablePath + " not read: " + ex.getMessage());
			return null;
		}
		pRc.cache.put(cacheKey, map);
		return map;
	}

	/** An attribute named in a configuration table: a path ("/Dimensions/length", "Dimensions/length") or a field name. */
	static String attributePath(final RenderContext pRc, final String pName)
	{
		final String n = pName == null ? "" : pName.trim();
		if (n.isEmpty())
		{
			return null;
		}
		final String stored = n.startsWith("./") ? n.substring(1) : n.startsWith("/") ? n : "/" + n;
		if (pRc.node(stored) != null)
		{
			return stored;
		}
		final String found = Behaviors.byName(pRc.root, n);
		return found == null ? stored : found;
	}

	private static String byName(final SchemaNode pNode, final String pName)
	{
		for (final SchemaNode c : pNode.getNodeChildren())
		{
			if (c.isTerminalValue() && pName.equalsIgnoreCase(c.getPathInAdaptation().getLastStep().format()))
			{
				return LayoutPaths.stored(c.getPathInAdaptation());
			}
			if (!c.isTerminalValue() && !c.isAssociationNode())
			{
				final String deep = Behaviors.byName(c, pName);
				if (deep != null)
				{
					return deep;
				}
			}
		}
		return null;
	}

	/**
	 * The engine (the same text in every pane; the last one defined wins, they are identical). Values as documented
	 * for each widget: a combo box gives {key, label}, a check box an array, a date widget a Date, a text a String.
	 */
	static final String ENGINE = "if(!window.ldfDyn){"
		+ "window.ldfV=function(p,t){var v;try{if(window.ebx_form_isNodeDefined&&!ebx_form_isNodeDefined(p))return undefined;v=ebx_form_getValue(p);}catch(e){return undefined;}"
		+ "var one=function(x){if(x==null)return '';if(x instanceof Date){var m=x.getMonth()+1,d=x.getDate();return x.getFullYear()+'-'+(m<10?'0':'')+m+'-'+(d<10?'0':'')+d;}"
		+ "if(typeof x==='object'&&x.key!=null)return String(x.key);return String(x);};"
		+ "if(t==='bool'){if(Array.isArray(v))return [v.length&&String(v[0])==='true'?'true':'false'];return [v==null||v===''?'':String(v)];}"
		+ "if(Array.isArray(v))return v.map(one).filter(function(s){return s!=='';});var s=one(v);return s===''?[]:[s];};"
		+ "window.ldfT=function(q,vals){var has=function(l){l=l||[];for(var i=0;i<vals.length;i++)for(var j=0;j<l.length;j++)if(String(l[j]).toLowerCase()===vals[i].toLowerCase())return true;return false;};"
		+ "var filled=vals.length>0&&!(vals.length===1&&vals[0]==='');"
		+ "if(q.filled)return filled;if(q.empty)return !filled;if(q.notIn)return !has(q.notIn);if(q['in'])return has(q['in']);return filled;};"
		+ "window.ldfShow=function(id,on){var e=document.getElementById(id);if(e)e.style.display=on?'':'none';};"
		+ "window.ldfDyn=function(){if(window.ldfBusy)return;window.ldfBusy=true;try{var R=window.ldfRules||[];"
		+ "for(var i=0;i<R.length;i++){var r=R[i];try{"
		+ "if(r.k==='show'||r.k==='mand'){var v=window.ldfV(r.q.c,r.q.ct);if(v===undefined)continue;var ok=window.ldfT(r.q,v);"
		+ "if(r.k==='show'){window.ldfShow(r.id,ok);if(!ok)for(var c=0;c<r.clear.length;c++){var cv=window.ldfV(r.clear[c],'');if(cv&&cv.length)ebx_form_setValue(r.clear[c],null);}}"
		+ "else ebx_form_setMandatoryIndicator(r.p,ok);}"
		+ "else if(r.k==='fam'){var fv=window.ldfV(r.c,r.ct);if(fv===undefined)continue;var key=fv.length?fv[0]:'';var list=null;"
		+ "for(var k in r.map)if(k.toLowerCase()===key.toLowerCase())list=r.map[k];var any=false;"
		+ "for(var f=0;f<r.fields.length;f++){var fd=r.fields[f],known=false,mine=null;"
		+ "for(var k2 in r.map){for(var a=0;a<r.map[k2].length;a++)if(r.map[k2][a].a===fd.a)known=true;}"
		+ "if(list)for(var b=0;b<list.length;b++)if(list[b].a===fd.a)mine=list[b];"
		+ "var on=!known||!!mine;window.ldfShow(fd.id,on);if(on)any=true;"
		+ "if(known){try{ebx_form_setMandatoryIndicator(fd.p,!!(mine&&mine.m));}catch(x){}}}"
		+ "if(r.hideEmpty)window.ldfShow(r.id,any);}"
		+ "}catch(x){}}}finally{window.ldfBusy=false;}};}";

	private Behaviors()
	{
	}
}
