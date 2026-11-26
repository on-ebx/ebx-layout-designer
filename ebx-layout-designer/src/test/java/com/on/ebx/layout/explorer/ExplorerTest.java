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
		ExplorerTest.counting();
		ExplorerTest.permissions();
		ExplorerTest.longLists();
		ExplorerTest.tree();
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
	private static Index index(final List<String> pSources, final Object[][] pRows, final Map<String, Map<String, String>> pLabels, final Map<String, Index.Tree> pTrees)
	{
		final Map<String, Integer> slots = new LinkedHashMap<String, Integer>();
		for (final String s : pSources)
		{
			slots.put(s, Integer.valueOf(slots.size()));
		}
		final String[] keys = new String[pRows.length];
		final String[] labels = new String[pRows.length];
		final String[][] text = new String[pRows.length][];
		for (int i = 0; i < pRows.length; i++)
		{
			keys[i] = String.valueOf(i + 1);
			labels[i] = String.valueOf(pRows[i][slots.get("/n").intValue()]);
			text[i] = new String[slots.size()];
			text[i][slots.get("/n").intValue()] = Index.norm(labels[i]);
		}
		return new Index(keys, labels, pRows, slots, pLabels, new HashSet<String>(), pTrees, new ArrayList<Index.Children>(), text, 1L, 0L);
	}

	private static final String CONFIG = "{'derived':{'ribTodo':{'type':'count','association':'/rib'}},'variants':[{'id':'std','layout':{"
		+ "'search':{'fields':['/n']},"
		+ "'facets':[{'id':'st','kind':'field','field':'/st'},{'id':'type','kind':'field','field':'/type'},"
		+ "{'id':'flag','kind':'flags','flags':[{'id':'RIB','label':{'en-US':'Bank'},'when':{'derived':'ribTodo','gt':0}},{'id':'QUAL','label':{'en-US':'To complete'},'when':{'field':'/sc','lt':70}}]},"
		+ "{'id':'band','kind':'ranges','field':'/sc','ranges':[{'code':'hi','min':80},{'code':'mid','min':50,'max':80},{'code':'lo','max':50}]}],"
		+ "'tiles':[{'id':'t1','flag':'QUAL'},{'id':'t2','when':{'field':'/st','in':['ACTIF']}},{'id':'t3','set':{'type':['FILIALE']}}],"
		+ "'sorts':[{'field':'/sc','dir':'desc'}]}}]}";

	private static Object[][] rows()
	{
		// n, st, type, sc, derived:ribTodo
		return new Object[][] { { "Bouygues Construction", "ACTIF", "FILIALE", Double.valueOf(100), Double.valueOf(0) },
			{ "Bureau Veritas", "ACTIF", "INDEPENDANT", Double.valueOf(100), Double.valueOf(1) },
			{ "Berner", "EN_VALIDATION", "INDEPENDANT", Double.valueOf(100), Double.valueOf(0) },
			{ "Boulonnerie Visserie", "ACTIF", "INDEPENDANT", Double.valueOf(65), Double.valueOf(1) },
			{ "Kiloutou", "ACTIF", "INDEPENDANT", Double.valueOf(100), Double.valueOf(0) },
			{ "Kiloutou ERP", "BLOQUE", "INDEPENDANT", Double.valueOf(40), Double.valueOf(0) } };
	}

	private static View view(final Spec pSpec, final Index pIndex, final BitSet pVisible, final Set<String> pHidden, final Map<Integer, Set<String>> pRowHidden)
	{
		return new View(pIndex, pSpec, pVisible, pHidden, pRowHidden, null, null);
	}

	private static BitSet all(final int n)
	{
		final BitSet b = new BitSet(n);
		b.set(0, n);
		return b;
	}

	private static void counting() throws Exception
	{
		final JsonNode root = ExplorerTest.j(ExplorerTest.CONFIG);
		final Index ix = ExplorerTest.index(Arrays.asList("/n", "/st", "/type", "/sc", "derived:ribTodo"), ExplorerTest.rows(), new HashMap<String, Map<String, String>>(), new HashMap<String, Index.Tree>());
		final View v = ExplorerTest.view(new Spec(root, r -> false, false), ix, ExplorerTest.all(6), null, null);
		Query.Request q = new Query.Request();
		Query.Result r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.rows.size() == 6 && r.all == 6, "no filter: every row");
		ExplorerTest.check(r.tiles.get("t1").intValue() == 2 && r.tiles.get("t2").intValue() == 4 && r.tiles.get("t3").intValue() == 1, "tiles " + r.tiles);
		ExplorerTest.check(ExplorerTest.count(r, "flag", "RIB") == 2 && ExplorerTest.count(r, "flag", "QUAL") == 2, "flags counted");
		ExplorerTest.check(ExplorerTest.count(r, "band", "lo") == 1 && ExplorerTest.count(r, "band", "mid") == 1, "ranges counted");
		ExplorerTest.check(r.rows.get(r.rows.size() - 1).intValue() == 5, "sorted by score, descending: the lowest last");

		q = new Query.Request();
		q.selected.put("st", new TreeSet<String>(Arrays.asList("ACTIF")));
		r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.rows.size() == 4, "status ACTIF: 4 rows");
		// a row counts for a facet when it passes every OTHER facet: the status facet still counts every status
		ExplorerTest.check(ExplorerTest.count(r, "st", "BLOQUE") == 1 && ExplorerTest.count(r, "st", "ACTIF") == 4, "own facet counts ignore its own selection");
		ExplorerTest.check(ExplorerTest.count(r, "type", "INDEPENDANT") == 3, "other facets count within the selection");

		q = new Query.Request();
		q.text = "kilou";
		r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.rows.size() == 2, "search 'kilou'");
		q = new Query.Request();
		q.tile = "t1";
		r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.rows.size() == 2, "tile QUAL as a filter");
	}

	private static int count(final Query.Result pResult, final String pFacet, final String pValue)
	{
		for (final Query.Item it : pResult.facets.get(pFacet).items)
		{
			if (it.v.equals(pValue))
			{
				return it.n;
			}
		}
		return 0;
	}

	private static void permissions() throws Exception
	{
		final JsonNode root = ExplorerTest.j(ExplorerTest.CONFIG);
		final Index ix = ExplorerTest.index(Arrays.asList("/n", "/st", "/type", "/sc", "derived:ribTodo"), ExplorerTest.rows(), new HashMap<String, Map<String, String>>(), new HashMap<String, Index.Tree>());
		// a record rule: rows 2 and 4 are not visible
		final BitSet visible = ExplorerTest.all(6);
		visible.clear(1);
		visible.clear(3);
		View v = ExplorerTest.view(new Spec(root, r -> false, false), ix, visible, null, null);
		Query.Result r = Query.run(v, new Query.Request(), Locale.ENGLISH);
		ExplorerTest.check(r.all == 4 && r.rows.size() == 4, "invisible records are not listed");
		ExplorerTest.check(ExplorerTest.count(r, "flag", "RIB") == 0, "invisible records are not counted in facets");
		ExplorerTest.check(r.tiles.get("t2").intValue() == 2, "invisible records are not counted in tiles: " + r.tiles);
		final Query.Request search = new Query.Request();
		search.text = "veritas";
		ExplorerTest.check(Query.run(v, search, Locale.ENGLISH).rows.isEmpty(), "an invisible record is not found by the search");

		// the score is hidden: its range facet, the flag reading it and the tile on that flag go; the sort too
		v = ExplorerTest.view(new Spec(root, rr -> false, false), ix, ExplorerTest.all(6), new HashSet<String>(Arrays.asList("/sc")), null);
		r = Query.run(v, new Query.Request(), Locale.ENGLISH);
		ExplorerTest.check(!r.facets.containsKey("band"), "a facet on a hidden field is removed");
		ExplorerTest.check(v.facets.stream().filter(f -> "flag".equals(f.id)).findFirst().get().flags.size() == 1, "a flag reading a hidden field is removed");
		ExplorerTest.check(!r.tiles.containsKey("t1") && r.tiles.containsKey("t2"), "a tile on a removed flag is removed");
		ExplorerTest.check(v.sorts.isEmpty(), "a sort on a hidden field is removed");

		// the name is hidden on one record only (a record-dependent rule): that record is not found by its name
		final Map<Integer, Set<String>> rowHidden = new HashMap<Integer, Set<String>>();
		rowHidden.put(Integer.valueOf(4), new HashSet<String>(Arrays.asList("/n")));
		v = ExplorerTest.view(new Spec(root, rr -> false, false), ix, ExplorerTest.all(6), null, rowHidden);
		final Query.Request q = new Query.Request();
		q.text = "kilou";
		ExplorerTest.check(Query.run(v, q, Locale.ENGLISH).rows.size() == 1, "a value hidden on one record is not searched there");
		// a status hidden on one record: that record has no status value in the facet
		rowHidden.clear();
		rowHidden.put(Integer.valueOf(5), new HashSet<String>(Arrays.asList("/st")));
		v = ExplorerTest.view(new Spec(root, rr -> false, false), ix, ExplorerTest.all(6), null, rowHidden);
		r = Query.run(v, new Query.Request(), Locale.ENGLISH);
		ExplorerTest.check(ExplorerTest.count(r, "st", "BLOQUE") == 0, "a value hidden on one record is not counted");

		// a computed value reading a hidden field is hidden too
		final JsonNode withCase = ExplorerTest.j("{'derived':{'band2':{'type':'case','cases':[{'when':{'field':'/sc','ge':80},'value':'hi'}],'else':'lo'}},"
			+ "'variants':[{'layout':{'facets':[{'id':'b2','kind':'field','field':'derived:band2'}]}}]}");
		final Index ix2 = ExplorerTest.index(Arrays.asList("/n", "/sc", "derived:band2"), new Object[][] { { "A", Double.valueOf(90), "hi" } },
			new HashMap<String, Map<String, String>>(), new HashMap<String, Index.Tree>());
		v = ExplorerTest.view(new Spec(withCase, rr -> false, false), ix2, ExplorerTest.all(1), new HashSet<String>(Arrays.asList("/sc")), null);
		ExplorerTest.check(v.hidden.contains("derived:band2") && v.facets.isEmpty(), "a computed value reading a hidden field is hidden");
	}

	private static void longLists() throws Exception
	{
		// 300 parent companies, a Zipf-like count: the facet sends the 10 most frequent, the selected one, or a search's matches
		final List<Object[]> rows = new ArrayList<Object[]>();
		final Map<String, String> labels = new LinkedHashMap<String, String>();
		for (int g = 0; g < 300; g++)
		{
			labels.put("G" + g, "Group " + g);
			for (int k = 0; k < Math.max(1, 30 - g / 10); k++)
			{
				rows.add(new Object[] { "R" + g + "-" + k, "G" + g });
			}
		}
		final Map<String, Map<String, String>> l = new HashMap<String, Map<String, String>>();
		l.put("/grp", labels);
		final Index ix = ExplorerTest.index(Arrays.asList("/n", "/grp"), rows.toArray(new Object[rows.size()][]), l, new HashMap<String, Index.Tree>());
		final JsonNode root = ExplorerTest.j("{'variants':[{'layout':{'facets':[{'id':'g','kind':'field','field':'/grp','top':10}]}}]}");
		final View v = ExplorerTest.view(new Spec(root, r -> false, false), ix, ExplorerTest.all(rows.size()), null, null);
		Query.Request q = new Query.Request();
		Query.Result r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.facets.get("g").items.size() == 10 && r.facets.get("g").total == 300, "10 values of 300");
		q = new Query.Request();
		q.selected.put("g", new TreeSet<String>(Arrays.asList("G250")));
		r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.facets.get("g").items.get(0).v.equals("G250") && r.facets.get("g").items.get(0).sel, "a selected value is always sent, first");
		q = new Query.Request();
		q.shown.put("g", Integer.valueOf(500));
		ExplorerTest.check(Query.run(v, q, Locale.ENGLISH).facets.get("g").items.size() == Query.MAX_VALUES, "never more than 50 values");
		q = new Query.Request();
		q.facetSearch.put("g", "group 29");
		r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.facets.get("g").total == 11, "a facet's own search: " + r.facets.get("g").total);
		final Query.FacetPage page = Query.values(v, "/grp", "group 1", 0, 5);
		ExplorerTest.check(page.items.size() == 5 && page.total == 111, "values page: " + page.total);
	}

	private static void tree() throws Exception
	{
		final Index.Tree t = new Index.Tree();
		t.label.put("DIR", "Direct");
		t.label.put("MAT", "Materials");
		t.parent.put("MAT", "DIR");
		t.label.put("MAT-VIT", "Glass");
		t.parent.put("MAT-VIT", "MAT");
		final Map<String, Index.Tree> trees = new HashMap<String, Index.Tree>();
		trees.put("/fam", t);
		final Index ix = ExplorerTest.index(Arrays.asList("/n", "/fam"), new Object[][] { { "A", "MAT-VIT" }, { "B", "MAT" }, { "C", "DIR" } },
			new HashMap<String, Map<String, String>>(), trees);
		final JsonNode root = ExplorerTest.j("{'variants':[{'layout':{'facets':[{'id':'f','kind':'tree','field':'/fam','parent':'/parent'}]}}]}");
		final View v = ExplorerTest.view(new Spec(root, r -> false, false), ix, ExplorerTest.all(3), null, null);
		final Query.Request q = new Query.Request();
		q.selected.put("f", new TreeSet<String>(Arrays.asList("MAT")));
		final Query.Result r = Query.run(v, q, Locale.ENGLISH);
		ExplorerTest.check(r.rows.size() == 2, "a tree node selects its descendants");
		final List<Query.Item> items = r.facets.get("f").items;
		ExplorerTest.check(items.size() == 3 && "DIR".equals(items.get(0).v) && items.get(2).d == 2, "tree values depth first");
	}
}
