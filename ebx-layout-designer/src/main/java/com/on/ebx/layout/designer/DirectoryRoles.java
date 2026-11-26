package com.on.ebx.layout.designer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationTable;
import com.onwbp.adaptation.RequestResult;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.Role;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.UserReference;
import com.orchestranetworks.service.directory.DirectoryHandler;

/**
 * The roles the designer offers for "visible for" and "read only for", and the users it can preview the form as. EBX
 * has no documented call that lists roles, so they are read from the default directory's own tables (dataset
 * {@code ebx-directory}, tables {@code /directory/roles} and {@code /directory/usersRoles}) with ordinary reads. With
 * another directory (LDAP, SSO) the tables do not exist: the lists come back empty and the designer takes typed role
 * names.
 */
final class DirectoryRoles
{
	private static final Path ROLES = Path.parse("/directory/roles");
	private static final Path USERS_ROLES = Path.parse("/directory/usersRoles");
	private static final int MAX_USERS = 300;

	private DirectoryRoles()
	{
	}

	/** {"roles": [{k, l}], "users": [{k, l, roles: [k…]}]}, the built-in administrator first. */
	static ObjectNode export(final Repository pRepository, final Session pSession, final Locale pLocale)
	{
		final ObjectNode out = LayoutStore.json().createObjectNode();
		final ArrayNode roles = out.putArray("roles");
		final ArrayNode users = out.putArray("users");
		final DirectoryHandler directory = pSession.getDirectory();
		final ObjectNode admin = roles.addObject();
		admin.put("k", "administrator");
		admin.put("l", DirectoryRoles.safe(() -> directory.displayProfile(Profile.ADMINISTRATOR, pLocale), "Administrator"));
		final Adaptation dataset = DirectoryRoles.dataset(pRepository);
		if (dataset == null)
		{
			return out;
		}
		final Map<String, String> names = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		DirectoryRoles.scan(dataset, DirectoryRoles.ROLES, r -> {
			final String name = r.getString(Path.parse("./name"));
			if (name != null && !name.isEmpty())
			{
				names.put(name, DirectoryRoles.safe(() -> directory.displaySpecificRole(Role.forSpecificRole(name), pLocale), name));
			}
		});
		for (final Map.Entry<String, String> e : names.entrySet())
		{
			final ObjectNode o = roles.addObject();
			o.put("k", e.getKey());
			o.put("l", e.getValue() == null || e.getValue().isEmpty() ? e.getKey() : e.getValue());
		}
		final Map<String, List<String>> byUser = new LinkedHashMap<String, List<String>>();
		DirectoryRoles.scan(dataset, DirectoryRoles.USERS_ROLES, r -> {
			final String user = r.getString(Path.parse("./user"));
			final String role = r.getString(Path.parse("./role"));
			if (user != null && role != null && (byUser.containsKey(user) || byUser.size() < DirectoryRoles.MAX_USERS))
			{
				byUser.computeIfAbsent(user, k -> new ArrayList<String>()).add(role);
			}
		});
		final Map<String, String> sorted = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		for (final String login : byUser.keySet())
		{
			sorted.put(DirectoryRoles.safe(() -> directory.displayUser(UserReference.forUser(login), pLocale), login) + "\u0000" + login, login);
		}
		for (final String login : sorted.values())
		{
			final ObjectNode o = users.addObject();
			o.put("k", login);
			o.put("l", DirectoryRoles.safe(() -> directory.displayUser(UserReference.forUser(login), pLocale), login));
			final ArrayNode rs = o.putArray("roles");
			for (final String r : byUser.get(login))
			{
				rs.add(r);
			}
		}
		return out;
	}

	private static Adaptation dataset(final Repository pRepository)
	{
		try
		{
			final AdaptationHome home = pRepository.lookupHome(HomeKey.forBranchName("ebx-directory"));
			final Adaptation dataset = home == null ? null : home.findAdaptationOrNull(AdaptationName.forName("ebx-directory"));
			return dataset != null && dataset.getSchemaNode().getNode(DirectoryRoles.ROLES) != null ? dataset : null;
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	private interface Row
	{
		void read(Adaptation pRow);
	}

	private static void scan(final Adaptation pDataset, final Path pTable, final Row pRow)
	{
		try
		{
			final AdaptationTable table = pDataset.getTable(pTable);
			final RequestResult rows = table.createRequest().execute();
			try
			{
				for (Adaptation r = rows.nextAdaptation(); r != null; r = rows.nextAdaptation())
				{
					pRow.read(r);
				}
			}
			finally
			{
				rows.close();
			}
		}
		catch (final RuntimeException ex)
		{
			// not the default directory's table
		}
	}

	private interface Text
	{
		String get();
	}

	private static String safe(final Text pText, final String pDefault)
	{
		try
		{
			String s = pText.get();
			// EBX writes a role as "[label]"
			if (s != null && s.length() > 2 && s.startsWith("[") && s.endsWith("]"))
			{
				s = s.substring(1, s.length() - 1).trim();
			}
			return s == null || s.isEmpty() ? pDefault : s;
		}
		catch (final RuntimeException ex)
		{
			return pDefault;
		}
	}
}
