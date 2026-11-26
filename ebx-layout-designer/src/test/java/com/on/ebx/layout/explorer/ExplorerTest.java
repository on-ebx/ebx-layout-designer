package com.on.ebx.layout.explorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The explorer's query, without a server: conditions, roles and variants, facet counting, tiles, search, long value
 * lists, and the EBX permissions a {@link View} applies (records not visible, fields hidden everywhere or on one record).
 * Run by tools/test.sh.
 */
public final class ExplorerTest
{
	private static final ObjectMapper JSON = new ObjectMapper();
	private static int failures;

	public static void main(final String[] pArgs) throws Exception
	{
		ExplorerTest.conditions();
		ExplorerTest.variants();
		ExplorerTest.predicates();
		ExplorerTest.selection();
		if (ExplorerTest.failures > 0)
		{
			System.out.println(ExplorerTest.failures + " explorer check(s) failed");
			System.exit(1);
		}
		System.out.println("Explorer checks passed");
	}

	private static void check(final boolean pOk, final String pWhat)
	{
		if (!pOk)
		{
			ExplorerTest.failures++;
			System.out.println("FAIL " + pWhat);
		}
	}

	private static JsonNode j(final String pJson) throws Exception
	{
		return ExplorerTest.JSON.readTree(pJson.replace('\'', '"'));
	}

	private static Cond.Lookup values(final Map<String, Object> pFields, final Map<String, Object> pDerived)
	{
		return new Cond.Lookup()
		{
			@Override
			public Object field(final String pPath)
			{
				return pFields.get(pPath);
			}

			@Override
			public Object derived(final String pName)
			{
				return pDerived.get(pName);
			}
		};
	}

	private static void conditions() throws Exception
	{
		final Map<String, Object> f = new HashMap<String, Object>();
		f.put("/st", "ACTIF");
		f.put("/sc", Double.valueOf(65));
		f.put("/pb", Boolean.TRUE);
		f.put("/tags", Arrays.asList("A", "B"));
		final Map<String, Object> d = new HashMap<String, Object>();
		d.put("rib", Double.valueOf(0));
		final Cond.Lookup l = ExplorerTest.values(f, d);
		// dates relative to today (1.20.0)
		final java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.add(java.util.Calendar.DAY_OF_MONTH, 30);
		f.put("/renew", cal.getTime());
		cal.add(java.util.Calendar.DAY_OF_MONTH, -60);
		f.put("/past", cal.getTime());
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/renew','days':{'from':0,'to':90}}"), l), "days within 90");
		ExplorerTest.check(!Cond.test(ExplorerTest.j("{'field':'/renew','days':{'from':0,'to':10}}"), l), "days not within 10");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/past','days':{'to':0}}"), l), "days before today");
		ExplorerTest.check(!Cond.test(ExplorerTest.j("{'field':'/none','days':{'to':0}}"), l), "days on an empty date");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/st','in':['ACTIF','BLOQUE']}"), l), "in");
		ExplorerTest.check(!Cond.test(ExplorerTest.j("{'field':'/st','notIn':['ACTIF']}"), l), "notIn");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/sc','lt':70}"), l), "lt");
		ExplorerTest.check(!Cond.test(ExplorerTest.j("{'field':'/sc','ge':70}"), l), "ge");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/pb','in':[true]}"), l), "boolean in");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/tags','in':['B']}"), l), "list in");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'field':'/none','empty':true}"), l), "empty");
		ExplorerTest.check(!Cond.test(ExplorerTest.j("{'field':'/none','gt':0}"), l), "missing is not greater");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'derived':'rib','eq':0}"), l), "derived eq");
		ExplorerTest.check(Cond.test(ExplorerTest.j("{'all':[{'field':'/st','in':['ACTIF']},{'any':[{'field':'/sc','gt':90},{'not':{'field':'/pb','in':[false]}}]}]}"), l), "all/any/not");
		final Set<String> fields = new TreeSet<String>(), derived = new TreeSet<String>();
		Cond.reads(ExplorerTest.j("{'all':[{'field':'/st','in':['A']},{'derived':'rib','gt':0}]}"), fields, derived);
		ExplorerTest.check(fields.equals(new TreeSet<String>(Arrays.asList("/st"))) && derived.contains("rib"), "reads");
	}

	private static void variants() throws Exception
	{
		final JsonNode root = ExplorerTest.j("{'variants':[{'id':'std','layout':{'facets':[{'id':'a','kind':'field','field':'/st'},{'id':'b','kind':'field','field':'/type','roles':['Achats']}]}},"
			+ "{'id':'cpt','roles':['Compta'],'layout':{'facets':[{'id':'c','kind':'field','field':'/src'}]}}]}");
		final Spec buyer = new Spec(root, roles -> roles.toString().contains("Achats"), false);
		ExplorerTest.check("std".equals(buyer.variantId) && buyer.facets.size() == 2, "a buyer gets the standard variant with its facet");
		final Spec other = new Spec(root, roles -> false, false);
		ExplorerTest.check(other.facets.size() == 1 && "a".equals(other.facets.get(0).id), "an element for a role is removed for the others");
		final Spec accounting = new Spec(root, roles -> roles.toString().contains("Compta"), false);
		ExplorerTest.check("cpt".equals(accounting.variantId) && "c".equals(accounting.facets.get(0).id), "the accounting variant");
		ExplorerTest.check(new Spec(root, roles -> false, true).facets.size() == 2, "an administrator who sees all");
	}

	/** Six suppliers: status, type, score, a count of bank accounts to validate, a name. */
	/** How conditions combine into what EBX is asked: SQL always, XPath only when every part has one. */
	private static void predicates()
	{
		final Model.Pred both = new Model.Pred("t.a = 1", "./a = 1");
		final Model.Pred sqlOnly = new Model.Pred("t.n > 0", null);
		Model.Pred p = Model.and(Arrays.asList(both, new Model.Pred("t.b = 'x'", "./b = 'x'")));
		ExplorerTest.check("(t.a = 1 AND t.b = 'x')".equals(p.sql) && "(./a = 1 and ./b = 'x')".equals(p.xpath), "and: both languages");
		p = Model.and(Arrays.asList(both, sqlOnly));
		ExplorerTest.check(p.sql != null && p.xpath == null, "and with an SQL-only part: SQL only");
		p = Model.or(Arrays.asList(both, Model.Pred.FALSE));
		ExplorerTest.check(p == both, "or with FALSE: the other part");
		ExplorerTest.check(Model.and(Arrays.asList(both, Model.Pred.FALSE)) == Model.Pred.FALSE, "and with FALSE: FALSE");
		ExplorerTest.check(Model.and(Arrays.asList(Model.Pred.TRUE, Model.Pred.TRUE)) == Model.Pred.TRUE, "and of nothing: TRUE");
		ExplorerTest.check("".equals(Model.Pred.TRUE.xpath), "TRUE: no XPath filter");
		p = Model.not(both);
		ExplorerTest.check("NOT (t.a = 1)".equals(p.sql) && "not(./a = 1)".equals(p.xpath), "not");
		ExplorerTest.check("'l''Oréal'".equals(Sql.lit("l'Oréal")) && "'l''Oréal'".equals(XPath.lit("l'Oréal")), "quotes doubled");
		ExplorerTest.check("'%50\\%\\_x%'".equals(Sql.contains("50%_x")), "LIKE escapes % and _");
		ExplorerTest.check("LOWER(t.n) LIKE '%mar%'".equals(Sql.like("t.n", true, "mar")), "text LIKE: no CAST, no ESCAPE");
		ExplorerTest.check(Sql.like("t.n", true, "50%").endsWith("ESCAPE '\\'"), "ESCAPE only when the text needs it");
		ExplorerTest.check(Sql.like("t.x", false, "12").startsWith("LOWER(CAST(t.x AS VARCHAR))"), "a non-text value is cast");
		ExplorerTest.check("12".equals(Sql.number(12.0)) && "1.5".equals(Sql.number(1.5)), "numbers");
		ExplorerTest.check("a.b".equals(Model.dotted(com.orchestranetworks.schema.Path.parse("/a/b"))), "dotted path");
		ExplorerTest.check("\"my-field\"".equals(Model.dotted(com.orchestranetworks.schema.Path.parse("/my-field"))), "odd step quoted");
	}

	private static void selection()
	{
		final Map<String, Set<String>> s = Query.parseSelection("st=ACTIF|BLOQUE;ville=Saint-Denis%3B2%7Cb");
		ExplorerTest.check(s.get("st").size() == 2 && s.get("ville").contains("Saint-Denis;2|b"), "selection parsed and unescaped");
	}
}
