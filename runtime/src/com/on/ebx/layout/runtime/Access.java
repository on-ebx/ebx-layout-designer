package com.on.ebx.layout.runtime;

import java.util.HashMap;
import java.util.Map;

import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationTable;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.service.AccessPermission;
import com.orchestranetworks.service.Session;
import com.orchestranetworks.service.SessionPermissions;

/**
 * What the current user may read, by EBX's own permissions ({@link SessionPermissions}, documented API). The form's
 * fields go through EBX's widgets, which apply them; every other read of a value (blocks, display widgets, rows of an
 * association, history) goes through here, so that a block never shows what EBX would hide. Set for the time a pane
 * is written ({@link #begin} / {@link #end}); outside of it (the designer, tests) everything is readable.
 */
final class Access
{
	private static final ThreadLocal<Access> CURRENT = new ThreadLocal<Access>();

	private final SessionPermissions permissions;
	private final Adaptation record;
	private final AdaptationTable table;
	private final Map<String, Boolean> cache = new HashMap<String, Boolean>();

	private Access(final Session pSession, final Adaptation pRecord, final AdaptationTable pTable)
	{
		this.permissions = pSession == null ? null : pSession.getPermissions();
		this.record = pRecord;
		this.table = pTable;
	}

	static void begin(final Session pSession, final Adaptation pRecord, final AdaptationTable pTable)
	{
		Access.CURRENT.set(new Access(pSession, pRecord, pTable));
	}

	static void end()
	{
		Access.CURRENT.remove();
	}

	/**
	 * Whether the user may read this field of this row (the record being created when {@code pRow} is null). A row of
	 * another kind (a history row) is checked with the same field of the record shown.
	 */
	static boolean canRead(final SchemaNode pNode, final Adaptation pRow)
	{
		final Access a = Access.CURRENT.get();
		if (a == null || a.permissions == null || pNode == null)
		{
			return true;
		}
		final String key = pNode.getPathInSchema().format() + "\u0001" + Access.rowKey(pRow);
		final Boolean known = a.cache.get(key);
		if (known != null)
		{
			return known.booleanValue();
		}
		boolean ok;
		try
		{
			final AccessPermission p = pRow == null ? a.permissions.getNodeAccessPermissionForCreate(pNode, a.table) : a.permissions.getNodeAccessPermission(pNode, pRow);
			ok = p == null || !p.isHidden();
		}
		catch (final RuntimeException ex)
		{
			ok = a.sameFieldOfRecord(pNode);
		}
		a.cache.put(key, Boolean.valueOf(ok));
		return ok;
	}

	/** Whether the user may see this record at all (a row of an association, a linked record). */
	static boolean canSee(final Adaptation pRow)
	{
		final Access a = Access.CURRENT.get();
		if (a == null || a.permissions == null || pRow == null)
		{
			return true;
		}
		final String key = "\u0002" + Access.rowKey(pRow);
		final Boolean known = a.cache.get(key);
		if (known != null)
		{
			return known.booleanValue();
		}
		boolean ok;
		try
		{
			final AccessPermission p = a.permissions.getAdaptationAccessPermission(pRow);
			ok = p == null || !p.isHidden();
		}
		catch (final RuntimeException ex)
		{
			ok = false;
		}
		a.cache.put(key, Boolean.valueOf(ok));
		return ok;
	}

	/** Fails closed: the same path on the record shown, else not readable. */
	private boolean sameFieldOfRecord(final SchemaNode pNode)
	{
		try
		{
			final AdaptationTable t = this.record != null ? this.record.getContainerTable() : this.table;
			final SchemaNode same = t == null ? null : t.getTableOccurrenceRootNode().getNode(pNode.getPathInAdaptation());
			if (same == null)
			{
				return false;
			}
			final AccessPermission p = this.record == null ? this.permissions.getNodeAccessPermissionForCreate(same, this.table)
				: this.permissions.getNodeAccessPermission(same, this.record);
			return p == null || !p.isHidden();
		}
		catch (final RuntimeException ex)
		{
			return false;
		}
	}

	private static String rowKey(final Adaptation pRow)
	{
		if (pRow == null)
		{
			return "";
		}
		try
		{
			return pRow.toXPathExpression();
		}
		catch (final RuntimeException ex)
		{
			return String.valueOf(System.identityHashCode(pRow));
		}
	}
}
