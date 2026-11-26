package com.on.ebx.layout.explorer;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Role;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.SessionPermissions;

/**
 * The explorers' entry point: a user's {@link View} of an explorer. Nothing of the data is kept: the {@link Model} (what
 * EBX is asked, labels of enumerations and of small referenced tables) is kept a minute per dataset, explorer and locale;
 * the permissions on fields and linked tables are read from EBX at each call ({@link SessionPermissions}); the records'
 * permissions are applied by EBX itself on every request and query, run with the user's session.
 */
public final class Views
{
	static final long MODEL_MS = 60_000L;
	private static final Map<String, Object[]> MODELS = new ConcurrentHashMap<String, Object[]>();

	private Views()
	{
	}

	static Model model(final Adaptation pDataset, final AdaptationTable pTable, final int pExplorer, final JsonNode pRoot, final Locale pLocale)
	{
		final String key = pDataset.getHome().getKey().getName() + "|" + pDataset.getAdaptationName().getStringName() + "|" + pTable.getTablePath().format() + "|"
			+ pExplorer + "|" + pLocale.toLanguageTag() + "|" + pRoot.toString().hashCode();
		final Object[] known = Views.MODELS.get(key);
		if (known != null && System.currentTimeMillis() - ((Long) known[1]).longValue() < Views.MODEL_MS)
		{
			return (Model) known[0];
		}
		final Model m = new Model(pDataset, pTable, pRoot, pLocale);
		Views.MODELS.put(key, new Object[] { m, Long.valueOf(System.currentTimeMillis()) });
		if (Views.MODELS.size() > 500)
		{
			Views.MODELS.entrySet().removeIf(e -> System.currentTimeMillis() - ((Long) e.getValue()[1]).longValue() > Views.MODEL_MS);
		}
		return m;
	}

	/** The user's view of an explorer, for the variant their roles choose. */
	public static View view(final Session pSession, final Adaptation pDataset, final AdaptationTable pTable, final int pExplorer, final JsonNode pRoot)
	{
		final Model model = Views.model(pDataset, pTable, pExplorer, pRoot, pSession.getLocale());
		final Spec spec = Views.spec(pRoot, pSession);
		final SessionPermissions perms = pSession.getPermissions();
		final SchemaNode root = pTable.getTableOccurrenceRootNode();
		// fields the user may not read in this dataset
		final Set<String> fields = new LinkedHashSet<String>();
		final Set<String> derived = new LinkedHashSet<String>();
		for (final String s : Spec.indexSources(pRoot))
		{
			if (s.startsWith(Spec.DERIVED))
			{
				derived.add(s.substring(Spec.DERIVED.length()));
			}
			else
			{
				fields.add(s);
			}
		}
		for (final JsonNode d : pRoot.path("derived"))
		{
			Spec.derivedReads(d, fields, new HashSet<String>());
		}
		final Set<String> hiddenFields = new HashSet<String>();
		for (final String f : fields)
		{
			final SchemaNode n = model.node(f);
			if (n != null && perms.getNodeAccessPermission(n, pDataset).isHidden())
			{
				hiddenFields.add(f);
			}
		}
		// computed values reading a table the user may not read
		final Set<String> hiddenTables = new HashSet<String>();
		pRoot.path("derived").fields().forEachRemaining(e -> {
			final String type = e.getValue().path("type").asText("");
			if ("count".equals(type) || "lookup".equals(type))
			{
				final AdaptationTable t = IndexBuilder.linkedTable(pDataset, pTable, e.getValue());
				if (t == null || perms.getNodeAccessPermission(t.getTableNode(), pDataset).isHidden())
				{
					hiddenTables.add(e.getKey());
				}
			}
			else if ("suspects".equals(type) && model.mds != null && perms.getAdaptationAccessPermission(model.mds).isHidden())
			{
				hiddenTables.add(e.getKey());
			}
			else if ("stateAt".equals(type))
			{
				AdaptationTable t = null;
				try
				{
					t = pDataset.getTable(com.orchestranetworks.schema.Path.parse(e.getValue().path("states").asText("")));
				}
				catch (final RuntimeException ex)
				{
					t = null;
				}
				if (t == null || perms.getNodeAccessPermission(t.getTableNode(), pDataset).isHidden())
				{
					hiddenTables.add(e.getKey());
				}
			}
		});
		return new View(model, spec, pSession, hiddenFields, hiddenTables);
	}

	public static Spec spec(final JsonNode pRoot, final Session pSession)
	{
		final boolean seesAll = pRoot.path("adminSeesAll").asBoolean(true) && pSession.isUserInRole(Profile.ADMINISTRATOR);
		return new Spec(pRoot, roles -> Views.inAnyRole(pSession, roles), seesAll);
	}

	/** Role names as the designer writes them ("Achats"; "administrator" for the built-in role). */
	public static boolean inAnyRole(final Session pSession, final JsonNode pRoles)
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

	/** True when the user may open this explorer: its openTo roles (none = everybody) and read access to the table. */
	public static boolean mayOpen(final Session pSession, final Adaptation pDataset, final AdaptationTable pTable, final JsonNode pRoot)
	{
		final JsonNode open = pRoot.path("openTo");
		if (open.size() > 0 && !pSession.isUserInRole(Profile.ADMINISTRATOR) && !Views.inAnyRole(pSession, open))
		{
			return false;
		}
		final SessionPermissions p = pSession.getPermissions();
		return !p.getAdaptationAccessPermission(pDataset).isHidden() && !p.getNodeAccessPermission(pTable.getTableNode(), pDataset).isHidden();
	}

	/** Drops what is kept of an explorer (after a save). */
	public static void forget(final int pExplorer)
	{
		final String mark = "|" + pExplorer + "|";
		Views.MODELS.keySet().removeIf(k -> k.contains(mark));
		Engine.forget(pExplorer);
	}
}
