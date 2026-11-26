package com.on.ebx.layout.explorer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.ValueContext;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;

/**
 * What EBX is asked, for one explorer on one table: each source (a field or a computed value) as an EBX SQL expression
 * and, when EBX's XPath can say it, as an XPath path; each condition as an SQL predicate and, when possible, an XPath
 * predicate. Nothing here reads records: the explorer's answers are EBX's answers ({@link Engine}), so a better EBX
 * engine is a better explorer. A computed value declared {@code provided} (Java) has no SQL form: it only shows on cards.
 *
 * <p>
 * SQL: the table is {@code "<table path>" t}; a foreign key is compared as {@code FK_AS_STRING(...)} (its formatted key,
 * the form the page uses); a linked table is reached with a correlated sub-query on its foreign key; Match and Merge
 * suspects come from the add-on's metadataset, registered in the query as {@code mds}.
 */
public final class Model
{
	/** A source as EBX can read it. */
	public static final class Src
	{
		public final String source;
		/** The field's node; null for a computed value. */
		public final SchemaNode node;
		/** {@code text}, {@code number}, {@code date}, {@code datetime}, {@code boolean}, {@code fk}, {@code list}, {@code other}. */
		public final String type;
		/** {@code ./a/b}; null when XPath cannot read the source. */
		public final String xpath;
		/** A scalar SQL expression on {@code t}: the value as the page keys it; null when SQL cannot read it. */
		public final String sql;
		/** The computed value's definition, else null. */
		public final JsonNode def;
		/** A Match and Merge linked field: its column in the metadataset's RecordMetadata table (SQL reads it there). */
		SchemaNode mdsColumn;

		Src(final String pSource, final SchemaNode pNode, final String pType, final String pXPath, final String pSql, final JsonNode pDef)
		{
			this.source = pSource;
			this.node = pNode;
			this.type = pType;
			this.xpath = pXPath;
			this.sql = pSql;
			this.def = pDef;
		}
	}

	/** A condition in EBX's two languages; {@code xpath} null when only SQL can say it. TRUE when there is no condition. */
	public static final class Pred
	{
		public static final Pred TRUE = new Pred("1 = 1", "");
		public static final Pred FALSE = new Pred("1 = 0", null);
		public final String sql;
		public final String xpath;
		/**
		 * The same condition as an XPath on the Match and Merge metadataset's RecordMetadata (one row per record), when it
		 * only reads the add-on's values: counted there directly for a user who sees every record (editor's advice).
		 */
		public final String meta;

		Pred(final String pSql, final String pXPath)
		{
			this(pSql, pXPath, null);
		}

		Pred(final String pSql, final String pXPath, final String pMeta)
		{
			this.sql = pSql;
			this.xpath = pXPath;
			this.meta = pMeta;
		}

		boolean isTrue()
		{
			return this == Pred.TRUE;
		}
	}

	/** A hierarchy for a {@code tree} facet: key of a record of the target table, its label and its parent's key. */
	public static final class Tree
	{
		public final Map<String, String> label = new LinkedHashMap<String, String>();
		public final Map<String, String> parent = new HashMap<String, String>();
	}

	final Adaptation dataset;
	final AdaptationTable table;
	final JsonNode root;
	final Locale locale;
	final SchemaNode rootNode;
	/** {@code "<table path>"}. */
	final String from;
	/** The Match and Merge metadataset, its suspect table ({@code Suspected} on 6.3, else {@code MatchingResult}). */
	final Adaptation mds;
	final String mdsTable;
	/** XPath of the add-on's "Has suspects" linked field, when the table carries it (6.3). */
	final String hasSuspectsXPath;
	private final Map<String, Src> sources = new ConcurrentHashMap<String, Src>();
	private final Map<String, Map<String, String>> labels = new ConcurrentHashMap<String, Map<String, String>>();
	/** The table's alias in the statement ({@code t}; another one in a sub-query on the same table). */
	final String t;
	private final AtomicInteger aliases;
	private ValueContext anyContext;

	Model(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot, final Locale pLocale)
	{
		this(pDataset, pTable, pRoot, pLocale, "t", new AtomicInteger());
		this.main = true;
	}

	/** The explorer's own table as {@code t}: the statements the engine writes on it get the metadataset's JOIN. */
	private boolean main;

	/** The same explorer read on another table or under another alias, sharing the alias counter (one statement, unique aliases). */
	private Model(final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot, final Locale pLocale, final String pAlias,
		final AtomicInteger pAliases)
	{
		this.t = pAlias;
		this.aliases = pAliases;
		this.dataset = pDataset;
		this.table = pTable;
		this.root = pRoot;
		this.locale = pLocale;
		this.rootNode = pTable.getTableOccurrenceRootNode();
		this.from = Model.quote(pTable.getTablePath().format());
		this.mds = IndexBuilder.metadataset(pDataset, pTable);
		String t = null;
		if (this.mds != null)
		{
			for (final String c : new String[] { "/root/Suspected", "/root/MatchingResult" })
			{
				if (t == null && this.mds.getSchemaNode().getNode(Path.parse(c)) != null)
				{
					t = c;
				}
			}
		}
		this.mdsTable = t;
		final SchemaNode has = this.node("/Linked_Field_HasSuspects");
		this.hasSuspectsXPath = has != null && Boolean.class.equals(has.getOccurrencesJavaClass()) ? "./Linked_Field_HasSuspects" : null;
	}

	/** The same explorer on another table of the dataset (a linked table), as {@code t}. */
	Model on(final AdaptationTable pTable)
	{
		return new Model(this.dataset, pTable, this.root, this.locale, "t", this.aliases);
	}

	/** The expression is one of these key strings. */
	Pred keysAs(final String pExpression, final Collection<String> pKeys)
	{
		final StringBuilder in = new StringBuilder();
		for (final String k : pKeys)
		{
			in.append(in.length() == 0 ? "" : ", ").append(Sql.lit(k));
		}
		return pKeys.isEmpty() ? Pred.FALSE : new Pred(pExpression + " IN (" + in + ")", null);
	}

	// ----- sources ---------------------------------------------------------------------------------------------------

	public Src src(final String pSource)
	{
		if (pSource == null)
		{
			return null;
		}
		Src s = this.sources.get(pSource);
		if (s == null)
		{
			s = this.compileSource(pSource);
			if (s != null)
			{
				this.sources.put(pSource, s);
			}
		}
		return s;
	}

	private Src compileSource(final String pSource)
	{
		if (pSource.startsWith(Spec.DERIVED))
		{
			final String name = pSource.substring(Spec.DERIVED.length());
			final JsonNode def = this.root.path("derived").get(name);
			if (def == null)
			{
				return null;
			}
			return new Src(pSource, null, Model.derivedType(def), null, this.derivedSql(def), def);
		}
		final SchemaNode n = this.node(pSource);
		if (n == null || !n.isTerminalValue() || n.isAssociationNode())
		{
			return null;
		}
		final String path = Model.dotted(n.getPathInAdaptation());
		final String type = Model.type(n);
		// a linked field (Match and Merge's) cannot be filtered in SQL, but XPath reads it
		final boolean sqlOk = !n.isLinkedNode();
		String sql = null;
		if (sqlOk)
		{
			sql = "fk".equals(type) ? "FK_AS_STRING(" + this.t + "." + path + ")" : "list".equals(type) ? null : this.t + "." + path;
		}
		final Src out = new Src(pSource, n, type, "." + n.getPathInAdaptation().format(), sql, null);
		if (!sqlOk)
		{
			out.mdsColumn = this.metadataColumn(n);
		}
		return out;
	}

	/** {@code Linked_Field_State} → {@code /root/RecordMetadata/state} of the metadataset, when it exists. */
	private SchemaNode metadataColumn(final SchemaNode pNode)
	{
		final String name = pNode.getPathInAdaptation().getLastStep() == null ? "" : pNode.getPathInAdaptation().getLastStep().format();
		if (this.mds == null || !name.startsWith("Linked_Field_") || name.length() <= "Linked_Field_".length())
		{
			return null;
		}
		final String rest = name.substring("Linked_Field_".length());
		final String column = Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
		try
		{
			final SchemaNode c = this.mds.getSchemaNode().getNode(Path.parse("/root/RecordMetadata/" + column));
			return c != null && c.isTerminalValue() ? c : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/**
	 * A condition on a Match and Merge linked field: XPath reads the field on the record; SQL reads the same value in the
	 * metadataset (one row per record, {@code functionalId} = the record's key), as the set of keys that satisfy it.
	 */
	private Pred viaMetadata(final Src pSrc, final java.util.function.Function<Src, Pred> pCondition)
	{
		final Pred x = pCondition.apply(pSrc);
		final String column = pSrc.mdsColumn.getPathInAdaptation().getLastStep().format();
		final String type = Model.type(pSrc.mdsColumn);
		// the same condition on the metadataset itself (XPath), for a direct count
		final Pred z = pCondition.apply(new Src(pSrc.source, pSrc.mdsColumn, type, "./" + column, null, null));
		final String meta = z == null || z == Pred.FALSE || z.isTrue() || z.xpath == null || z.xpath.isEmpty() ? null : z.xpath;
		final Pred[] y = new Pred[1];
		final String sql = this.metadataSql(a -> {
			y[0] = pCondition.apply(new Src(pSrc.source, pSrc.mdsColumn, type, null, a + "." + column, null));
			return y[0] == null || y[0] == Pred.FALSE || y[0].isTrue() ? null : y[0].sql;
		});
		if (y[0] == Pred.FALSE || x == Pred.FALSE)
		{
			return Pred.FALSE;
		}
		if (sql == null)
		{
			return x;
		}
		return new Pred(sql, x == null ? null : x.xpath, meta);
	}

	/** The alias of the metadataset's RecordMetadata joined to the explorer's table ({@code Engine} adds the join). */
	static final String META = "rmd";

	/** The metadataset holds RecordMetadata (Match and Merge 6.3), with this column. */
	boolean hasMetadata(final String pColumn)
	{
		try
		{
			return this.mds != null && this.mds.getSchemaNode().getNode(Path.parse("/root/RecordMetadata/" + pColumn)) != null;
		}
		catch (final RuntimeException ex)
		{
			return false;
		}
	}

	/** {@code FK_AS_STRING(alias.functionalId)}: functionalId is a foreign key to the business table, compared as a string. */
	String metadataKey(final String pAlias)
	{
		final SchemaNode functionalId = this.mds.getSchemaNode().getNode(Path.parse("/root/RecordMetadata/functionalId"));
		return functionalId != null && functionalId.getFacetOnTableReference() != null ? "FK_AS_STRING(" + pAlias + ".functionalId)" : pAlias + ".functionalId";
	}

	/**
	 * A condition on the record's metadataset row in SQL. On the explorer's table ({@code t}) the row is JOINed once
	 * ({@code LEFT JOIN … rmd}, added by the engine: 1.3 s against 4.5 s for the sub-query on 576,692 records, editor's
	 * measure); a record without a row does not hold the condition. Elsewhere (a linked table), a set of keys.
	 */
	String metadataSql(final java.util.function.Function<String, String> pCondition)
	{
		if (this.main)
		{
			// a record without a metadata row reads NULL: false in a WHERE; NOT() turns it into true (see not())
			final String c = pCondition.apply(Model.META);
			return c == null ? null : "(" + c + ")";
		}
		final String a = this.alias("r");
		final String c = pCondition.apply(a);
		return c == null ? null : this.t + ".\"$pk\" IN (SELECT " + this.metadataKey(a) + " FROM mds." + Model.quote("/root/RecordMetadata") + " " + a + " WHERE " + c + ")";
	}

	static String type(final SchemaNode pNode)
	{
		if (pNode.getMaxOccurs() > 1)
		{
			return "list";
		}
		if (pNode.getFacetOnTableReference() != null)
		{
			return "fk";
		}
		final Class<?> c = pNode.getOccurrencesJavaClass();
		if (c != null && Number.class.isAssignableFrom(c))
		{
			return "number";
		}
		if (Boolean.class.equals(c))
		{
			return "boolean";
		}
		if (c != null && Date.class.isAssignableFrom(c))
		{
			final com.orchestranetworks.schema.SchemaTypeName x = pNode.getXsTypeName();
			return x != null && (com.orchestranetworks.schema.SchemaTypeName.XS_DATETIME.equals(x) || "dateTime".equals(x.getNameWithoutPrefix())) ? "datetime" : "date";
		}
		return String.class.equals(c) ? "text" : "other";
	}

	private static String derivedType(final JsonNode pDef)
	{
		switch (pDef.path("type").asText(""))
		{
		case "count":
		case "sameValue":
			return "number";
		case "test":
			return "boolean";
		case "suspects":
			return "list";
		case "case":
		{
			boolean numbers = true;
			for (final JsonNode c : pDef.path("cases"))
			{
				numbers &= c.path("value").isNumber();
			}
			numbers &= pDef.path("else").isNumber() || pDef.path("else").isNull() || pDef.path("else").isMissingNode();
			return numbers && pDef.path("cases").size() > 0 ? "number" : "text";
		}
		case "lookup":
		case "firstOf":
			return "text";
		default:
			return "other";
		}
	}

	/** {@code a.b.c} from {@code /a/b/c}, each step quoted when it is not a plain identifier. */
	static String dotted(final Path pPath)
	{
		final StringBuilder b = new StringBuilder();
		for (final String step : pPath.format().split("/"))
		{
			if (step.isEmpty() || ".".equals(step))
			{
				continue;
			}
			if (b.length() > 0)
			{
				b.append('.');
			}
			b.append(step.matches("[A-Za-z][A-Za-z0-9]*") ? step : Model.quote(step));
		}
		return b.toString();
	}

	static String quote(final String pIdentifier)
	{
		return '"' + pIdentifier.replace("\"", "\"\"") + '"';
	}

	SchemaNode node(final String pPath)
	{
		try
		{
			return pPath == null || pPath.isEmpty() || pPath.startsWith(Spec.DERIVED) ? null : this.rootNode.getNode(LayoutPaths.relative(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private String alias(final String pPrefix)
	{
		return pPrefix + this.aliases.incrementAndGet();
	}

	/** The SQL of a computed value, on {@code t}; null when SQL cannot compute it. */
	private String derivedSql(final JsonNode pDef)
	{
		switch (pDef.path("type").asText(""))
		{
		case "count":
		{
			final String[] link = this.link(pDef);
			if (link == null)
			{
				return null;
			}
			final String c = this.alias("c");
			final String where = this.childPred(pDef.get("where"), link[0], c);
			return where == null ? null
				: "(SELECT COUNT(*) FROM " + Model.quote(link[0]) + " " + c + " WHERE FK_AS_STRING(" + c + "." + link[1] + ") = " + this.t + ".\"$pk\"" + where + ")";
		}
		case "lookup":
		{
			final String[] link = this.link(pDef);
			final AdaptationTable child = link == null ? null : this.dataset.getTable(Path.parse(link[0]));
			final SchemaNode f = child == null ? null : Model.childNode(child, pDef.path("field").asText(""));
			if (f == null)
			{
				return null;
			}
			final String a = this.alias("c"), b = this.alias("c"), e = this.alias("c");
			final String value = "fk".equals(Model.type(f)) ? "FK_AS_STRING(%s." + Model.dotted(f.getPathInAdaptation()) + ")"
				: "%s." + Model.dotted(f.getPathInAdaptation());
			final String linkA = " WHERE FK_AS_STRING(" + a + "." + link[1] + ") = " + this.t + ".\"$pk\"";
			final String linkB = " WHERE FK_AS_STRING(" + b + "." + link[1] + ") = " + this.t + ".\"$pk\"";
			final String linkE = " WHERE FK_AS_STRING(" + e + "." + link[1] + ") = " + this.t + ".\"$pk\"";
			final String any = "(SELECT MIN(" + String.format(value, e) + ") FROM " + Model.quote(link[0]) + " " + e + linkE + ")";
			if (!pDef.hasNonNull("prefer"))
			{
				return any;
			}
			final String preferA = this.childPred(pDef.get("prefer"), link[0], a), preferB = this.childPred(pDef.get("prefer"), link[0], b);
			if (preferA == null || preferB == null)
			{
				return any;
			}
			// written CASE WHEN EXISTS: a COALESCE of two sub-queries is refused by the 6.3 RC optimizer once grouped
			return "CASE WHEN EXISTS (SELECT 1 FROM " + Model.quote(link[0]) + " " + a + linkA + preferA + ") THEN (SELECT MIN(" + String.format(value, b)
				+ ") FROM " + Model.quote(link[0]) + " " + b + linkB + preferB + ") ELSE " + any + " END";
		}
		case "sameValue":
		{
			final Src f = this.src(pDef.path("field").asText(""));
			if (f == null || f.sql == null || f.node == null)
			{
				return null;
			}
			final String s = this.alias("s");
			final Src same = new Model(this.dataset, this.table, this.root, this.locale, s, this.aliases).src(f.source);
			final String other = same == null ? null : same.sql;
			if (other == null)
			{
				return null;
			}
			return "CASE WHEN " + f.sql + " IS NULL THEN 0 ELSE (SELECT COUNT(*) FROM " + this.from + " " + s + " WHERE " + other + " = " + f.sql + ") END";
		}
		case "suspects":
		{
			if (this.mdsTable == null)
			{
				return null;
			}
			final String s = this.alias("m");
			return "(SELECT COUNT(*) FROM mds." + Model.quote(this.mdsTable) + " " + s + " WHERE " + this.suspectRow(s) + " AND (" + s + ".sourceId = " + this.t + ".\"$pk\" OR "
				+ s + ".targetId = " + this.t + ".\"$pk\"))";
		}
		case "test":
		{
			final Pred p = this.pred(pDef.get("when"));
			return p == null ? null : "CASE WHEN " + p.sql + " THEN TRUE ELSE FALSE END";
		}
		case "case":
		{
			final StringBuilder b = new StringBuilder("CASE");
			for (final JsonNode c : pDef.path("cases"))
			{
				final Pred p = this.pred(c.get("when"));
				if (p == null)
				{
					return null;
				}
				b.append(" WHEN ").append(p.sql).append(" THEN ").append(Model.sqlScalar(c.get("value")));
			}
			return b.append(" ELSE ").append(Model.sqlScalar(pDef.get("else"))).append(" END").toString();
		}
		case "firstOf":
		{
			final StringBuilder b = new StringBuilder("CASE");
			for (final JsonNode it : pDef.path("items"))
			{
				final Src s = this.src(Spec.source(it));
				final Pred filled = s == null ? null : this.leaf(s, "filled", null);
				final Pred when = this.pred(it.get("when"));
				if (s == null || s.sql == null || filled == null || when == null)
				{
					return null;
				}
				b.append(" WHEN ").append(filled.sql).append(" AND ").append(when.sql).append(" THEN ").append(Sql.lit(it.path("prefix").asText(""))).append(" || ")
					.append(this.labelSql(s));
			}
			return b.append(" ELSE NULL END").toString();
		}
		default:
			return null;
		}
	}

	/** The value's text for a {@code firstOf}: a static enumeration's label, else the value. */
	private String labelSql(final Src pSrc)
	{
		final Map<String, String> l = pSrc.node == null ? null : this.enumeration(pSrc);
		if (l == null || l.isEmpty())
		{
			return "CAST(" + pSrc.sql + " AS VARCHAR)";
		}
		final StringBuilder b = new StringBuilder("CASE");
		for (final Map.Entry<String, String> e : l.entrySet())
		{
			b.append(" WHEN ").append(pSrc.sql).append(" = ").append(Sql.lit(e.getKey())).append(" THEN ").append(Sql.lit(e.getValue()));
		}
		return b.append(" ELSE CAST(").append(pSrc.sql).append(" AS VARCHAR) END").toString();
	}

	/** {@code [child table path, foreign key as dotted SQL]} of a {@code count} or {@code lookup}; null when not found. */
	private String[] link(final JsonNode pDef)
	{
		final AdaptationTable child = IndexBuilder.linkedTable(this.dataset, this.table, pDef);
		final Path fk = IndexBuilder.linkedKey(this.table, pDef, child);
		return child == null || fk == null ? null : new String[] { child.getTablePath().format(), Model.dotted(fk) };
	}

	private static SchemaNode childNode(final AdaptationTable pChild, final String pPath)
	{
		try
		{
			return pPath.isEmpty() ? null : pChild.getTableOccurrenceRootNode().getNode(LayoutPaths.relative(pPath));
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	/** A condition on a linked table's row, as {@code " AND <predicate>"} on the given alias; "" when none; null when SQL cannot say it. */
	private String childPred(final JsonNode pCond, final String pChildTable, final String pAlias)
	{
		if (pCond == null || pCond.isNull() || pCond.size() == 0)
		{
			return "";
		}
		final Model child = new Model(this.dataset, this.dataset.getTable(Path.parse(pChildTable)), this.root, this.locale, pAlias, this.aliases);
		final Pred p = child.pred(pCond);
		return p == null || p.sql == null ? null : " AND (" + p.sql + ")";
	}

	String suspectRow(final String pAlias)
	{
		return this.mdsTable.endsWith("MatchingResult") ? "CAST(" + pAlias + ".lastResult AS VARCHAR) = '1'" : "1 = 1";
	}

	/**
	 * "The computed value is at least n", as a set the record's key belongs to ({@code IN (SELECT … GROUP BY … HAVING
	 * COUNT(*) >= n)}): EBX runs it as one join, where the correlated sub-query of the value itself would be run once per
	 * record. For {@code count}, {@code sameValue} and {@code suspects}; null for anything else.
	 */
	private String atLeast(final Src pSrc, final long pN)
	{
		final String type = pSrc.def == null ? "" : pSrc.def.path("type").asText("");
		if (pN <= 0 && ("count".equals(type) || "sameValue".equals(type) || "suspects".equals(type)))
		{
			return "1 = 1";
		}
		switch (type)
		{
		case "count":
		{
			final String[] link = this.link(pSrc.def);
			if (link == null)
			{
				return null;
			}
			final String c = this.alias("c");
			final String where = this.childPred(pSrc.def.get("where"), link[0], c);
			if (where == null)
			{
				return null;
			}
			final String fk = "FK_AS_STRING(" + c + "." + link[1] + ")";
			return this.t + ".\"$pk\" IN (SELECT " + fk + " FROM " + Model.quote(link[0]) + " " + c + " WHERE 1 = 1" + where + " GROUP BY " + fk + " HAVING COUNT(*) >= "
				+ pN + ")";
		}
		case "sameValue":
		{
			final Src f = this.src(pSrc.def.path("field").asText(""));
			final String a = this.alias("s");
			final Src same = f == null || f.sql == null ? null : new Model(this.dataset, this.table, this.root, this.locale, a, this.aliases).src(f.source);
			if (same == null || same.sql == null)
			{
				return null;
			}
			return f.sql + " IN (SELECT " + same.sql + " FROM " + this.from + " " + a + " WHERE " + same.sql + " IS NOT NULL GROUP BY " + same.sql + " HAVING COUNT(*) >= "
				+ pN + ")";
		}
		case "suspects":
		{
			if (this.mdsTable == null)
			{
				return null;
			}
			final String a = this.alias("m"), b = this.alias("m");
			final String table = "mds." + Model.quote(this.mdsTable);
			if (pN == 1)
			{
				// one IN over a UNION, as the editor advised (one set of keys instead of two IN joined by OR)
				final boolean all = !this.mdsTable.endsWith("MatchingResult");
				return this.t + ".\"$pk\" IN (SELECT " + a + ".sourceId FROM " + table + " " + a + (all ? "" : " WHERE " + this.suspectRow(a)) + " UNION SELECT " + b
					+ ".targetId FROM " + table + " " + b + (all ? "" : " WHERE " + this.suspectRow(b)) + ")";
			}
			final String x = this.alias("x");
			return this.t + ".\"$pk\" IN (SELECT " + x + ".k FROM (SELECT " + a + ".sourceId AS k FROM " + table + " " + a + " WHERE " + this.suspectRow(a)
				+ " UNION ALL SELECT " + b + ".targetId AS k FROM " + table + " " + b + " WHERE " + this.suspectRow(b) + ") " + x + " GROUP BY " + x + ".k HAVING COUNT(*) >= "
				+ pN + ")";
		}
		default:
			return null;
		}
	}

	/** A comparison of a counted value with a number, written with {@link #atLeast} sets; null when the value is not counted. */
	private Pred counted(final Src pSrc, final String pOp, final double pN)
	{
		if (this.atLeast(pSrc, 1) == null)
		{
			return null;
		}
		final long floor = (long) Math.floor(pN), ceil = (long) Math.ceil(pN);
		switch (pOp)
		{
		case "gt":
			return new Pred(this.atLeast(pSrc, floor + 1), null);
		case "ge":
			return new Pred(this.atLeast(pSrc, ceil), null);
		case "lt":
			return Model.not(new Pred(this.atLeast(pSrc, ceil), null));
		case "le":
			return Model.not(new Pred(this.atLeast(pSrc, floor + 1), null));
		default:
		{
			final Pred eq = pN != Math.rint(pN) ? Pred.FALSE
				: Model.and(java.util.Arrays.asList(new Pred(this.atLeast(pSrc, floor), null), Model.not(new Pred(this.atLeast(pSrc, floor + 1), null))));
			return "eq".equals(pOp) ? eq : Model.not(eq);
		}
		}
	}

	// ----- conditions ------------------------------------------------------------------------------------------------

	/** A condition of the explorer's format; null when EBX cannot evaluate it (a {@code provided} value). */
	public Pred pred(final JsonNode pCond)
	{
		if (pCond == null || pCond.isNull() || pCond.isMissingNode() || pCond.size() == 0)
		{
			return Pred.TRUE;
		}
		if (pCond.has("all") || pCond.has("any"))
		{
			final boolean all = pCond.has("all");
			final List<Pred> parts = new ArrayList<Pred>();
			for (final JsonNode c : pCond.get(all ? "all" : "any"))
			{
				final Pred p = this.pred(c);
				if (p == null)
				{
					return null;
				}
				parts.add(p);
			}
			return all ? Model.and(parts) : Model.or(parts);
		}
		if (pCond.has("not"))
		{
			final Pred p = this.pred(pCond.get("not"));
			return p == null ? null : Model.not(p);
		}
		if (pCond.has("days"))
		{
			return this.pred(Cond.days(pCond));
		}
		final Src s = this.src(pCond.has("derived") ? Spec.DERIVED + pCond.path("derived").asText() : pCond.path("field").asText(""));
		if (s == null)
		{
			return null;
		}
		if (s.mdsColumn != null && s.sql == null)
		{
			return this.viaMetadata(s, x -> this.leafCondition(pCond, x));
		}
		return this.leafCondition(pCond, s);
	}

	/** One simple condition on a source (no all / any / not / days). */
	private Pred leafCondition(final JsonNode pCond, final Src s)
	{
		if (pCond.has("in"))
		{
			return this.in(s, Model.texts(pCond.get("in")));
		}
		if (pCond.has("notIn"))
		{
			final Pred in = this.in(s, Model.texts(pCond.get("notIn")));
			return in == null ? null : Model.not(in);
		}
		if (pCond.path("filled").asBoolean(false))
		{
			return this.leaf(s, "filled", null);
		}
		if (pCond.path("empty").asBoolean(false))
		{
			return this.leaf(s, "empty", null);
		}
		for (final String op : new String[] { "gt", "lt", "ge", "le", "eq", "ne" })
		{
			if (pCond.has(op))
			{
				return this.leaf(s, op, pCond.get(op));
			}
		}
		return Pred.TRUE;
	}

	private static List<String> texts(final JsonNode pArray)
	{
		final List<String> out = new ArrayList<String>();
		for (final JsonNode x : pArray)
		{
			out.add(x.isTextual() ? x.asText() : x.toString());
		}
		return out;
	}

	/** The source's value is one of the keys (the form the page sends: enumeration code, formatted key, true/false, number). */
	public Pred in(final Src pSrc, final Collection<String> pKeys)
	{
		if (pKeys.isEmpty())
		{
			return Pred.FALSE;
		}
		if (pSrc.mdsColumn != null && pSrc.sql == null)
		{
			return this.viaMetadata(pSrc, x -> x.mdsColumn == null ? this.in(x, pKeys) : this.inDirect(x, pKeys));
		}
		return this.inDirect(pSrc, pKeys);
	}

	private Pred inDirect(final Src pSrc, final Collection<String> pKeys)
	{
		if ("list".equals(pSrc.type) && pSrc.def != null && "suspects".equals(pSrc.def.path("type").asText()))
		{
			// the records suspect of one of these records, as two sets of keys
			if (this.mdsTable == null)
			{
				return Pred.FALSE;
			}
			final String a = this.alias("m"), b = this.alias("m");
			final StringBuilder list = new StringBuilder();
			for (final String k : pKeys)
			{
				list.append(list.length() == 0 ? "" : ", ").append(Sql.lit(k));
			}
			final String table = "mds." + Model.quote(this.mdsTable);
			return new Pred("(" + this.t + ".\"$pk\" IN (SELECT " + a + ".sourceId FROM " + table + " " + a + " WHERE " + this.suspectRow(a) + " AND " + a + ".targetId IN ("
				+ list + ")) OR " + this.t + ".\"$pk\" IN (SELECT " + b + ".targetId FROM " + table + " " + b + " WHERE " + this.suspectRow(b) + " AND " + b
				+ ".sourceId IN (" + list + ")))", null);
		}
		final StringBuilder sql = new StringBuilder();
		final StringBuilder xpath = new StringBuilder();
		for (final String k : pKeys)
		{
			if (sql.length() > 0)
			{
				sql.append(", ");
				xpath.append(" or ");
			}
			sql.append(this.sqlLiteral(pSrc, k));
			xpath.append(this.xpathEquals(pSrc, k));
		}
		if ("list".equals(pSrc.type))
		{
			// a list holds the value when one of its items is one of the keys
			final String u = this.alias("u");
			final String s = pSrc.node == null || pSrc.node.isLinkedNode() || pSrc.node.getFacetOnTableReference() != null ? null
				: "EXISTS (SELECT 1 FROM UNNEST(" + this.t + "." + Model.dotted(pSrc.node.getPathInAdaptation()) + ") AS " + u + "(x) WHERE " + u + ".x IN (" + sql + "))";
			return s == null && pSrc.xpath == null ? null : new Pred(s, pSrc.xpath == null ? null : "(" + xpath + ")");
		}
		if (pSrc.sql == null && pSrc.xpath == null)
		{
			return null;
		}
		return new Pred(pSrc.sql == null ? null : pSrc.sql + " IN (" + sql + ")", pSrc.xpath == null ? null : "(" + xpath + ")");
	}

	/** {@code filled}, {@code empty}, or a comparison with a literal. */
	Pred leaf(final Src pSrc, final String pOp, final JsonNode pRef)
	{
		final boolean suspects = pSrc.def != null && "suspects".equals(pSrc.def.path("type").asText());
		final String derivedType = pSrc.def == null ? "" : pSrc.def.path("type").asText("");
		if (("filled".equals(pOp) || "empty".equals(pOp)) && ("count".equals(derivedType) || "sameValue".equals(derivedType)))
		{
			// a count is a number, 0 included: never empty
			return "filled".equals(pOp) ? Pred.TRUE : Pred.FALSE;
		}
		if ("filled".equals(pOp) || "empty".equals(pOp))
		{
			final boolean filled = "filled".equals(pOp);
			String sql;
			String xpath = null;
			String meta = null;
			if (suspects)
			{
				if (this.mdsTable == null && this.hasSuspectsXPath == null)
				{
					return filled ? Pred.FALSE : Pred.TRUE;
				}
				final String one = this.hasMetadata("hasSuspects") ? this.metadataSql(a -> a + ".hasSuspects = TRUE") : this.atLeast(pSrc, 1);
				sql = one == null ? null : filled ? one : one.contains(Model.META + ".") ? "NOT (COALESCE(" + one + ", FALSE))" : "NOT (" + one + ")";
				meta = this.hasMetadata("hasSuspects") ? (filled ? "./hasSuspects = 'true'" : "not(./hasSuspects = 'true')") : null;
				if (this.hasSuspectsXPath != null)
				{
					// the add-on's own flag: EBX keeps it, XPath reads it
					xpath = filled ? this.hasSuspectsXPath + " = 'true'" : "not(" + this.hasSuspectsXPath + " = 'true')";
				}
			}
			else if ("list".equals(pSrc.type))
			{
				sql = pSrc.node == null || pSrc.node.isLinkedNode() ? null : "CARDINALITY(" + this.t + "." + Model.dotted(pSrc.node.getPathInAdaptation()) + ")" + (filled ? " > 0" : " = 0");
				if (!filled && sql != null)
				{
					sql = "(" + this.t + "." + Model.dotted(pSrc.node.getPathInAdaptation()) + " IS NULL OR " + sql + ")";
				}
			}
			else
			{
				sql = pSrc.sql == null ? null
					: "text".equals(pSrc.type) ? (filled ? "(" + pSrc.sql + " IS NOT NULL AND " + pSrc.sql + " <> '')" : "(" + pSrc.sql + " IS NULL OR " + pSrc.sql + " = '')")
						: pSrc.sql + (filled ? " IS NOT NULL" : " IS NULL");
				xpath = pSrc.xpath == null ? null : (filled ? "osd:is-not-null(" : "osd:is-null(") + pSrc.xpath + ")";
			}
			return sql == null && xpath == null ? null : new Pred(sql, xpath, meta);
		}
		final String ref = pRef.isTextual() ? pRef.asText() : pRef.toString();
		final Double number = pRef.isNumber() ? Double.valueOf(pRef.asDouble()) : Cond.number(ref);
		final String sqlOp = Model.sqlOperator(pOp);
		if (number != null && pSrc.def != null)
		{
			// a count read through a linked table, a same-value count, suspects: compared as sets, never record by record
			final Pred c = this.counted(pSrc, pOp, number.doubleValue());
			if (c != null)
			{
				return c;
			}
		}
		if (suspects || "number".equals(pSrc.type) || "list".equals(pSrc.type))
		{
			if (number == null)
			{
				return Pred.FALSE;
			}
			String value = pSrc.sql;
			if ("list".equals(pSrc.type) && !suspects)
			{
				value = pSrc.node == null || pSrc.node.isLinkedNode() ? null : "COALESCE(CARDINALITY(" + this.t + "." + Model.dotted(pSrc.node.getPathInAdaptation()) + "), 0)";
			}
			final String n = Sql.number(number.doubleValue());
			final String sql = value == null ? null : "ne".equals(pOp) ? "(" + value + " IS NULL OR " + value + " <> " + n + ")" : value + " " + sqlOp + " " + n;
			final String xpath = "list".equals(pSrc.type) || pSrc.xpath == null ? null
				: "ne".equals(pOp) ? "not(" + pSrc.xpath + " = " + n + ")" : pSrc.xpath + " " + Model.xpathOperator(pOp) + " " + n;
			return sql == null && xpath == null ? null : new Pred(sql, xpath);
		}
		if ("date".equals(pSrc.type) || "datetime".equals(pSrc.type))
		{
			final String day = ref.length() >= 10 ? ref.substring(0, 10) : ref;
			if (!day.matches("\\d{4}-\\d{2}-\\d{2}"))
			{
				return Pred.FALSE;
			}
			final String lit = "date".equals(pSrc.type) ? "DATE '" + day + "'" : "TIMESTAMP '" + day + " 00:00:00'";
			final String sql = pSrc.sql == null ? null : "ne".equals(pOp) ? "(" + pSrc.sql + " IS NULL OR " + pSrc.sql + " <> " + lit + ")" : pSrc.sql + " " + sqlOp + " " + lit;
			return new Pred(sql, null);
		}
		// text (and booleans): equality as the page writes it, order as text
		final String lit = this.sqlLiteral(pSrc, ref);
		if ("eq".equals(pOp))
		{
			return new Pred(pSrc.sql == null ? null : pSrc.sql + " = " + lit, pSrc.xpath == null ? null : this.xpathEquals(pSrc, ref));
		}
		if ("ne".equals(pOp))
		{
			return new Pred(pSrc.sql == null ? null : "(" + pSrc.sql + " IS NULL OR " + pSrc.sql + " <> " + lit + ")",
				pSrc.xpath == null ? null : "not(" + this.xpathEquals(pSrc, ref) + ")");
		}
		return pSrc.sql == null ? null : new Pred(pSrc.sql + " " + sqlOp + " " + lit, null);
	}

	private static String sqlOperator(final String pOp)
	{
		switch (pOp)
		{
		case "gt": return ">";
		case "lt": return "<";
		case "ge": return ">=";
		case "le": return "<=";
		case "eq": return "=";
		default: return "<>";
		}
	}

	private static String xpathOperator(final String pOp)
	{
		return "ne".equals(pOp) ? "!=" : Model.sqlOperator(pOp);
	}

	/** A value range of a {@code ranges} facet: min included, max excluded. */
	public Pred range(final Src pSrc, final Double pMin, final Double pMax)
	{
		final List<Pred> parts = new ArrayList<Pred>();
		if (pMin != null)
		{
			parts.add(this.leaf(pSrc, "ge", com.on.ebx.layout.runtime.LayoutStore.json().getNodeFactory().numberNode(pMin.doubleValue())));
		}
		if (pMax != null)
		{
			parts.add(this.leaf(pSrc, "lt", com.on.ebx.layout.runtime.LayoutStore.json().getNodeFactory().numberNode(pMax.doubleValue())));
		}
		if (parts.isEmpty())
		{
			return this.leaf(pSrc, "filled", null);
		}
		return parts.contains(null) ? null : Model.and(parts);
	}

	/** The record is one of these (formatted primary keys). */
	public Pred keys(final Collection<String> pKeys)
	{
		if (pKeys.isEmpty())
		{
			return Pred.FALSE;
		}
		final StringBuilder sql = new StringBuilder();
		final StringBuilder xpath = new StringBuilder();
		final Path[] pk = this.table.getPrimaryKeySpec();
		for (final String k : pKeys)
		{
			sql.append(sql.length() == 0 ? "" : ", ").append(Sql.lit(k));
			if (pk.length == 1)
			{
				xpath.append(xpath.length() == 0 ? "" : " or ").append(".").append(pk[0].format().startsWith("/") ? "" : "/").append(pk[0].format()).append(" = ")
					.append(XPath.lit(k));
			}
		}
		return new Pred("" + this.t + ".\"$pk\" IN (" + sql + ")", pk.length == 1 ? "(" + xpath + ")" : null);
	}

	/**
	 * The search: the text in one of the searched sources (case-insensitive, as EBX compares). A static enumeration or a
	 * foreign key is also found by its label.
	 */
	public Pred text(final List<String> pSources, final String pText)
	{
		final String q = pText.trim();
		final List<Pred> parts = new ArrayList<Pred>();
		for (final String source : pSources)
		{
			final Src s = this.src(source);
			if (s == null)
			{
				continue;
			}
			final List<Pred> mine = new ArrayList<Pred>();
			if (s.def != null && "lookup".equals(s.def.path("type").asText()))
			{
				// a value read through a linked table: the records one of whose linked rows holds the text (one join, not a
				// sub-query per record)
				final Pred linked = this.linkedText(s.def, q);
				if (linked != null)
				{
					parts.add(linked);
				}
				continue;
			}
			if ("text".equals(s.type) || "fk".equals(s.type) || "other".equals(s.type))
			{
				mine.add(new Pred(s.sql == null ? null : Sql.like(s.sql, "text".equals(s.type) || "fk".equals(s.type), q.toLowerCase(this.locale)),
					s.xpath == null ? null : "osd:contains-case-insensitive(" + s.xpath + ", " + XPath.lit(q) + ")"));
			}
			else if ("number".equals(s.type) && Cond.number(q) != null && s.sql != null)
			{
				mine.add(this.leaf(s, "eq", com.on.ebx.layout.runtime.LayoutStore.json().getNodeFactory().textNode(q)));
			}
			// values found by their label
			final Map<String, String> labels = s.node == null ? null : "fk".equals(s.type) ? this.targetLabels(s) : this.enumeration(s);
			if (labels != null)
			{
				final List<String> hit = new ArrayList<String>();
				final String nq = Model.norm(q);
				for (final Map.Entry<String, String> e : labels.entrySet())
				{
					if (Model.norm(e.getValue()).contains(nq))
					{
						hit.add(e.getKey());
					}
				}
				if (!hit.isEmpty() && hit.size() <= 500)
				{
					final Pred in = this.in(s, hit);
					if (in != null)
					{
						mine.add(in);
					}
				}
			}
			for (final Pred p : mine)
			{
				if (p != null && (p.sql != null || p.xpath != null))
				{
					parts.add(p);
				}
			}
		}
		return parts.isEmpty() ? Pred.FALSE : Model.or(parts);
	}

	/** Records with a linked row whose {@code lookup} field holds the text. */
	private Pred linkedText(final JsonNode pDef, final String pText)
	{
		final String[] link = this.link(pDef);
		final AdaptationTable child = link == null ? null : this.dataset.getTable(Path.parse(link[0]));
		final SchemaNode f = child == null ? null : Model.childNode(child, pDef.path("field").asText(""));
		if (f == null)
		{
			return null;
		}
		final String c = this.alias("c");
		final String value = ("fk".equals(Model.type(f)) ? "FK_AS_STRING(" + c + "." + Model.dotted(f.getPathInAdaptation()) + ")" : c + "." + Model.dotted(f.getPathInAdaptation()));
		final boolean text = "text".equals(Model.type(f)) || "fk".equals(Model.type(f));
		return new Pred(this.t + ".\"$pk\" IN (SELECT FK_AS_STRING(" + c + "." + link[1] + ") FROM " + Model.quote(link[0]) + " " + c + " WHERE "
			+ Sql.like(value, text, pText.toLowerCase(this.locale)) + ")", null);
	}

	static Pred and(final List<Pred> pParts)
	{
		final List<Pred> kept = new ArrayList<Pred>();
		for (final Pred p : pParts)
		{
			if (p == Pred.FALSE)
			{
				return Pred.FALSE;
			}
			if (!p.isTrue())
			{
				kept.add(p);
			}
		}
		if (kept.isEmpty())
		{
			return Pred.TRUE;
		}
		if (kept.size() == 1)
		{
			return kept.get(0);
		}
		return Model.join(kept, " AND ", " and ");
	}

	static Pred or(final List<Pred> pParts)
	{
		final List<Pred> kept = new ArrayList<Pred>();
		for (final Pred p : pParts)
		{
			if (p.isTrue())
			{
				return Pred.TRUE;
			}
			if (p != Pred.FALSE)
			{
				kept.add(p);
			}
		}
		if (kept.isEmpty())
		{
			return Pred.FALSE;
		}
		if (kept.size() == 1)
		{
			return kept.get(0);
		}
		return Model.join(kept, " OR ", " or ");
	}

	private static Pred join(final List<Pred> pParts, final String pSql, final String pXPath)
	{
		final StringBuilder s = new StringBuilder("(");
		final StringBuilder x = new StringBuilder("(");
		final StringBuilder mt = new StringBuilder("(");
		boolean sqlOk = true, xpathOk = true, metaOk = true;
		for (int i = 0; i < pParts.size(); i++)
		{
			final Pred p = pParts.get(i);
			metaOk &= p.meta != null;
			if (metaOk)
			{
				mt.append(i == 0 ? "" : pXPath).append(p.meta);
			}
			sqlOk &= p.sql != null;
			xpathOk &= p.xpath != null && !p.xpath.isEmpty();
			if (sqlOk)
			{
				s.append(i == 0 ? "" : pSql).append(p.sql);
			}
			if (xpathOk)
			{
				x.append(i == 0 ? "" : pXPath).append(p.xpath);
			}
		}
		return !sqlOk && !xpathOk ? null
			: new Pred(sqlOk ? s.append(')').toString() : null, xpathOk ? x.append(')').toString() : null, metaOk ? mt.append(')').toString() : null);
	}

	static Pred not(final Pred pPred)
	{
		if (pPred.isTrue())
		{
			return Pred.FALSE;
		}
		if (pPred == Pred.FALSE)
		{
			return Pred.TRUE;
		}
		// over the joined metadataset a missing row is NULL: NOT must still hold for it
		final String sql = pPred.sql == null ? null : pPred.sql.contains(Model.META + ".") ? "NOT (COALESCE(" + pPred.sql + ", FALSE))" : "NOT (" + pPred.sql + ")";
		return new Pred(sql, pPred.xpath == null ? null : "not(" + pPred.xpath + ")",
			pPred.meta == null ? null : "not(" + pPred.meta + ")");
	}

	private String sqlLiteral(final Src pSrc, final String pKey)
	{
		if ("number".equals(pSrc.type) && Cond.number(pKey) != null)
		{
			return Sql.number(Cond.number(pKey).doubleValue());
		}
		if ("boolean".equals(pSrc.type))
		{
			return "true".equalsIgnoreCase(pKey) ? "TRUE" : "FALSE";
		}
		if ("date".equals(pSrc.type) && pKey.matches("\\d{4}-\\d{2}-\\d{2}"))
		{
			return "DATE '" + pKey + "'";
		}
		return Sql.lit(pKey);
	}

	private String xpathEquals(final Src pSrc, final String pKey)
	{
		if (pSrc.xpath == null)
		{
			return null;
		}
		if ("number".equals(pSrc.type) && Cond.number(pKey) != null)
		{
			return pSrc.xpath + " = " + Sql.number(Cond.number(pKey).doubleValue());
		}
		if ("date".equals(pSrc.type) || "datetime".equals(pSrc.type))
		{
			return "date-equal(" + pSrc.xpath + ", " + XPath.lit(pKey) + ")";
		}
		return pSrc.xpath + " = " + XPath.lit(pKey);
	}

	private static String sqlScalar(final JsonNode pValue)
	{
		if (pValue == null || pValue.isNull())
		{
			return "NULL";
		}
		if (pValue.isNumber())
		{
			return Sql.number(pValue.asDouble());
		}
		if (pValue.isBoolean())
		{
			return pValue.asBoolean() ? "TRUE" : "FALSE";
		}
		return Sql.lit(pValue.asText());
	}

	// ----- labels ----------------------------------------------------------------------------------------------------

	/** A static enumeration's (or a boolean's) values and labels in the model's order; null for anything else. */
	public Map<String, String> enumeration(final Src pSrc)
	{
		if (pSrc.node == null)
		{
			return null;
		}
		final Map<String, String> known = this.labels.get("enum|" + pSrc.source);
		if (known != null)
		{
			return known.isEmpty() ? null : known;
		}
		final Map<String, String> out = new LinkedHashMap<String, String>();
		final SchemaNode n = pSrc.node;
		try
		{
			if ("boolean".equals(pSrc.type))
			{
				for (final Boolean b : new Boolean[] { Boolean.TRUE, Boolean.FALSE })
				{
					out.put(b.toString(), IndexBuilder.display(n, b, this.anyContext(), this.locale));
				}
			}
			else if (n.getFacetEnumeration() != null && n.getFacetOnTableReference() == null && n.getFacetEnumeration().isEnumerationStatic())
			{
				final List<?> values = n.getFacetEnumeration().getValues();
				if (values != null && values.size() <= 200)
				{
					for (final Object v : values)
					{
						out.put(Cond.key(v), IndexBuilder.display(n, v, this.anyContext(), this.locale));
					}
				}
			}
		}
		catch (final RuntimeException ex)
		{
			out.clear();
		}
		this.labels.put("enum|" + pSrc.source, out);
		return out.isEmpty() ? null : out;
	}

	/** A plain field's few distinct values (key to label), computed once per model; empty when it holds many. */
	Map<String, String> fewValues(final Src pSrc, final java.util.function.Supplier<Map<String, String>> pCompute)
	{
		Map<String, String> known = this.labels.get("few|" + pSrc.source);
		if (known == null)
		{
			known = pCompute.get();
			this.labels.put("few|" + pSrc.source, known);
		}
		return known.isEmpty() ? null : known;
	}

	/** Labels of the records a foreign key may point to, when the target table is small (search by label); else null. */
	Map<String, String> targetLabels(final Src pSrc)
	{
		final Map<String, String> known = this.labels.get("fk|" + pSrc.source);
		if (known != null)
		{
			return known.isEmpty() ? null : known;
		}
		final Map<String, String> out = new LinkedHashMap<String, String>();
		final AdaptationTable target = IndexBuilder.treeTable(this.dataset, this.table, pSrc.source);
		if (target != null)
		{
			final RequestResult r = target.createRequest().execute();
			try
			{
				if (r.getSize() <= 2000)
				{
					for (Adaptation a; (a = r.nextAdaptation()) != null;)
					{
						out.put(a.getOccurrencePrimaryKey().format(), a.getLabel(this.locale));
					}
				}
			}
			finally
			{
				r.close();
			}
		}
		this.labels.put("fk|" + pSrc.source, out);
		return out.isEmpty() ? null : out;
	}

	/** The label of one value of a source (an enumeration's label, a foreign key's target record label); else the value. */
	public String label(final Src pSrc, final String pKey)
	{
		if (pSrc == null || pKey == null)
		{
			return pKey;
		}
		final Map<String, String> e = this.enumeration(pSrc);
		if (e != null && e.containsKey(pKey))
		{
			return e.get(pKey);
		}
		if ("fk".equals(pSrc.type))
		{
			final Map<String, String> t = this.targetLabels(pSrc);
			if (t != null)
			{
				return t.containsKey(pKey) ? t.get(pKey) : pKey;
			}
			try
			{
				final AdaptationTable target = IndexBuilder.treeTable(this.dataset, this.table, pSrc.source);
				final Adaptation a = target == null ? null : target.lookupAdaptationByPrimaryKey(com.onwbp.adaptation.PrimaryKey.parseString(pKey));
				return a == null ? pKey : a.getLabel(this.locale);
			}
			catch (final RuntimeException ex)
			{
				return pKey;
			}
		}
		if (pSrc.node != null && pSrc.node.getFacetEnumeration() != null)
		{
			try
			{
				return IndexBuilder.display(pSrc.node, pKey, this.anyContext(), this.locale);
			}
			catch (final RuntimeException ex)
			{
				return pKey;
			}
		}
		return pKey;
	}

	/** The hierarchy of a {@code tree} facet's target table (read once per model). */
	public Tree tree(final String pField, final String pParent)
	{
		final String key = "tree|" + pField + "|" + pParent;
		Tree t = (Tree) this.trees.get(key);
		if (t != null)
		{
			return t;
		}
		t = new Tree();
		final AdaptationTable target = IndexBuilder.treeTable(this.dataset, this.table, pField);
		final Path parent = pParent == null || pParent.isEmpty() ? null : LayoutPaths.relative(pParent);
		if (target != null && parent != null)
		{
			final Tree tree = t;
			IndexBuilder.each(target, r -> {
				final String k = r.getOccurrencePrimaryKey().format();
				tree.label.put(k, r.getLabel(this.locale));
				Object p;
				try
				{
					p = r.get(parent);
				}
				catch (final RuntimeException ex)
				{
					p = null;
				}
				final String pk = Cond.key(p);
				// a row that is its own parent (a NACE section carries its own code) is a root
				if (pk != null && !pk.isEmpty() && !pk.equals(k))
				{
					tree.parent.put(k, pk);
				}
			});
		}
		this.trees.put(key, t);
		return t;
	}

	private final Map<String, Object> trees = new ConcurrentHashMap<String, Object>();

	/** A value context of any record, for enumeration labels. */
	private ValueContext anyContext()
	{
		if (this.anyContext == null)
		{
			final RequestResult r = this.table.createRequest().execute();
			try
			{
				final Adaptation a = r.nextAdaptation();
				this.anyContext = a == null ? null : a.createValueContext();
			}
			finally
			{
				r.close();
			}
		}
		return this.anyContext;
	}

	/** Lower case, accents removed: how a label is compared with the typed text. */
	public static String norm(final String pText)
	{
		return pText == null ? "" : java.text.Normalizer.normalize(pText, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	static String day(final Date pDate)
	{
		return new SimpleDateFormat("yyyy-MM-dd").format(pDate);
	}
}
