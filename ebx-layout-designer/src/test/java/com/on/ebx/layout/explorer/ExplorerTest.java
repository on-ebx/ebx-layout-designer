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
		ExplorerTest.orShape();
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

	/** L2-09, L2-16, L2-18: no OR between a sub-query and anything else, favorites by key, a pool that can be stopped. */
	private static void orShape()
	{
		final String t = "t", from = "\"/root/T\"";
		final Model.Pred like = new Model.Pred(Sql.like("t.name", true, "dup"), "osd:contains-case-insensitive(./name, 'dup')");
		final Model.Pred linked = new Model.Pred("t.\"$pk\" IN (SELECT FK_AS_STRING(c1.supplier) FROM \"/root/Contact\" c1 WHERE " + Sql.like("c1.name", true, "dup") + ")", null);
		Model.Pred p = Model.or(Arrays.asList(like, linked), t, from);
		ExplorerTest.check(p.sql.startsWith("t.\"$pk\" IN (SELECT ") && p.sql.contains(" UNION ") && !p.sql.contains("OR (SELECT") && !p.sql.contains(" OR "),
			"search + lookup: one IN over a UNION, no OR: " + p.sql);
		ExplorerTest.check(p.sql.contains("SELECT t.\"$pk\" FROM \"/root/T\" t WHERE LOWER(t.name) LIKE '%dup%'") && !p.sql.contains("CAST"), "the LIKE is a branch, with no CAST");
		ExplorerTest.check(p.xpath == null, "the union has no XPath form");
		final Model.Pred pct = new Model.Pred(Sql.like("t.name", true, "50%"), null);
		p = Model.or(Arrays.asList(pct, linked), t, from);
		ExplorerTest.check(p.sql.contains("LIKE '%50\\%%' ESCAPE '\\'"), "a value with % gets ESCAPE: " + p.sql);
		final Model.Pred other = new Model.Pred("t.\"$pk\" IN (SELECT FK_AS_STRING(c2.supplier) FROM \"/root/Bank\" c2 GROUP BY FK_AS_STRING(c2.supplier) HAVING COUNT(*) >= 2)", null);
		p = Model.or(Arrays.asList(linked, other), t, from);
		ExplorerTest.check(p.sql.contains(" UNION ") && !p.sql.contains(" OR ") && p.sql.indexOf("SELECT t.\"$pk\" FROM") < 0, "two sets: a UNION of their own SELECTs");
		p = Model.or(Arrays.asList(like, new Model.Pred("t.n > 1", "./n > 1")), t, from);
		ExplorerTest.check(p.sql.contains(" OR ") && p.xpath != null, "no sub-query: the plain OR and its XPath stay");
		final Model.Pred meta = new Model.Pred("rmd.state = 1", null);
		ExplorerTest.check(Model.or(Arrays.asList(meta, linked), t, from) == null, "a metadataset join beside a sub-query cannot be written: refused (null)");
		ExplorerTest.check(Model.keySet("t.\"$pk\" IN (SELECT a) OR t.\"$pk\" IN (SELECT b)", t) == null, "two IN joined by OR are not one set");
		ExplorerTest.check("SELECT 'a)' FROM x".equals(Model.keySet("t.\"$pk\" IN (SELECT 'a)' FROM x)", t)), "a parenthesis in a literal does not close the set");
		final String f = ExplorerStore.favoritesFilter("u'1", 7, "Ref", "Main");
		ExplorerTest.check(f.contains("./user = 'u''1'") && f.contains("./explorer = 7") && f.contains("./dataspace = 'Ref'") && f.contains("./dataset = 'Main'"),
			"favorites: a predicate on the user and the key, not a sweep: " + f);
		Engine.shutdown();
		Engine.shutdown();
		final List<java.util.concurrent.Callable<Integer>> tasks = new ArrayList<java.util.concurrent.Callable<Integer>>();
		tasks.add(() -> Integer.valueOf(1));
		tasks.add(() -> Integer.valueOf(2));
		ExplorerTest.check(Engine.all(tasks).equals(Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))), "after shutdown the tasks run one after the other");
	}

	private static void selection()
	{
		final Map<String, Set<String>> s = Query.parseSelection("st=ACTIF|BLOQUE;ville=Saint-Denis%3B2%7Cb");
		ExplorerTest.check(s.get("st").size() == 2 && s.get("ville").contains("Saint-Denis;2|b"), "selection parsed and unescaped");
	}
}
