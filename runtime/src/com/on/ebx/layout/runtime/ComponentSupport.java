package com.on.ebx.layout.runtime;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaNode;
import com.orchestranetworks.ui.UIHttpManagerComponent;
import com.orchestranetworks.ui.form.UIFormContext;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * What a registered component ({@link LayoutComponent}) may use of the runtime (1.27.0), so that a block written in
 * another module reads values, permissions and associations exactly as the built-in blocks do: values as EBX displays
 * them, the field and record permissions of the user, the rows of an association read once per display, the links
 * that open a record in EBX's modal pop-up. Obtained with {@link #current()} inside {@link LayoutComponent#write}.
 */
public final class ComponentSupport
{
	private static final ThreadLocal<ComponentSupport> CURRENT = new ThreadLocal<ComponentSupport>();

	private final RenderContext rc;
	private final UIFormPaneWriter writer;

	private ComponentSupport(final RenderContext pRc, final UIFormPaneWriter pWriter)
	{
		this.rc = pRc;
		this.writer = pWriter;
	}

	/** The support of the component being written on this thread; null outside {@link LayoutComponent#write}. */
	public static ComponentSupport current()
	{
		return ComponentSupport.CURRENT.get();
	}

	static void enter(final RenderContext pRc, final UIFormPaneWriter pWriter)
	{
		ComponentSupport.CURRENT.set(new ComponentSupport(pRc, pWriter));
	}

	static void leave()
	{
		ComponentSupport.CURRENT.remove();
	}

	public UIFormContext context()
	{
		return this.rc.context;
	}

	public Locale locale()
	{
		return this.rc.locale;
	}

	/** The record shown; null while a record is created. */
	public Adaptation record()
	{
		return this.rc.record;
	}

	public boolean creating()
	{
		return this.rc.creating;
	}

	/** The root node of the form's table. */
	public SchemaNode root()
	{
		return this.rc.root;
	}

	/** A text of the layout ({@code {"en-US": "…", "fr-FR": "…"}} or a plain string) in the user's locale. */
	public String text(final JsonNode pText)
	{
		return this.rc.text(pText);
	}

	/** The label of a field of the form's table. */
	public String label(final String pStoredPath)
	{
		return this.rc.label(pStoredPath);
	}

	/** The value of a field of the record shown (or being created), as stored. */
	public Object raw(final String pStoredPath)
	{
		return this.rc.raw(pStoredPath);
	}

	/** The rows of an association of the record the user may see, at most {@code pMax}, read once per display. */
	public List<Adaptation> rows(final String pAssociation, final int pMax)
	{
		return this.rc.rows(pAssociation, pMax);
	}

	/** The record a foreign-key field of the form points to, when the user may read the field and see the record. */
	public Adaptation linked(final String pForeignKey)
	{
		return MoreBlocks.linked(this.rc, pForeignKey);
	}

	public String recordUrl(final Adaptation pRecord)
	{
		return MoreBlocks.recordUrl(this.rc, pRecord);
	}

	/**
	 * The attributes of a link ({@code href} and {@code onclick}, escaped) that opens a record in EBX's own modal pop-up,
	 * a sub-session of the form (the table views' preview mechanism): the user comes back to the form on close. When the
	 * pop-up cannot be built, a plain link in a new tab.
	 */
	public String openAttributes(final Adaptation pRecord)
	{
		final String url = this.recordUrl(pRecord);
		String js = null;
		if (this.writer != null)
		{
			try
			{
				final UIHttpManagerComponent wc = this.rc.context.createWebComponentForSubSession();
				wc.selectInstanceOrOccurrence(pRecord);
				js = this.writer.buildButtonPreview(wc).getJavaScriptCommand();
			}
			catch (final RuntimeException ex)
			{
				js = null;
			}
		}
		return js == null ? "href=\"" + LayoutPane.esc(url) + "\" target=\"_blank\" rel=\"noopener\""
			: "href=\"" + LayoutPane.esc(url) + "\" onclick=\"" + LayoutPane.esc(js) + ";return false;\"";
	}

	/** True the first time a key is asked during one display of the form: a style sheet or a script written once. */
	public boolean once(final String pKey)
	{
		return this.rc.cache.put("\u0003once:" + pKey, Boolean.TRUE) == null;
	}

	/** The EBX user id; null without a session. */
	public String userId()
	{
		try
		{
			return this.rc.context.getSession().getUserReference().getUserId();
		}
		catch (final RuntimeException ex)
		{
			return null;
		}
	}

	public static String esc(final String pText)
	{
		return LayoutPane.esc(pText);
	}

	/** A path as the layout stores it ({@code /name}, {@code ./name}, {@code /root/T/name}) as a path relative to a record. */
	public static Path relative(final String pStoredPath)
	{
		return LayoutPaths.relative(pStoredPath);
	}

	public static SchemaNode node(final Adaptation pRow, final String pStoredPath)
	{
		return Values.node(pRow, pStoredPath);
	}

	/** The value of a field of a row, as stored; null when the user may not read it. */
	public static Object raw(final Adaptation pRow, final String pStoredPath)
	{
		return Values.raw(pRow, pStoredPath);
	}

	/** The value as EBX displays it in this locale (foreign key label, enumeration label, formatted date); "" when hidden. */
	public static String display(final Adaptation pRow, final String pStoredPath, final Locale pLocale)
	{
		return Values.display(pRow, pStoredPath, pLocale);
	}

	/** A stored value as a key: a foreign key's formatted primary key, an enumeration's code, a number's plain text. */
	public static String key(final Object pValue)
	{
		return Values.key(pValue);
	}

	public static LocalDate date(final Object pValue)
	{
		return Values.date(pValue);
	}

	/** The tone class ({@code ldf-t-ok}…) a {@code colors} map gives a value, "" when it gives none. */
	public static String tone(final JsonNode pColors, final String pKey, final String pDisplayed)
	{
		return Values.tone(pColors, pKey, pDisplayed);
	}

	/** Whether the user may see this record (record permission). */
	public static boolean canSee(final Adaptation pRow)
	{
		return Access.canSee(pRow);
	}

	/** Whether the user may read this field of this row (field permission). */
	public static boolean canRead(final SchemaNode pNode, final Adaptation pRow)
	{
		return Access.canRead(pNode, pRow);
	}

	public static ObjectMapper json()
	{
		return LayoutStore.json();
	}
}
