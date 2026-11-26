package com.on.ebx.layout.runtime;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.ui.form.UIFormContext;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * A block a module writes in Java and registers by name ({@link LayoutComponents#register}), so that the designer
 * can place it like any other block. The component receives its block as configured (title, note, parameters) and
 * writes inside the panel the runtime has opened for it. Since 1.27.0, {@link ComponentSupport#current()} gives the
 * component the runtime's values, permissions, association rows and record links while it writes.
 */
public interface LayoutComponent
{
	/** Shown in the designer's palette. */
	String getLabel(Locale pLocale);

	void write(UIFormPaneWriter pWriter, UIFormContext pContext, JsonNode pBlock, Locale pLocale);

	/**
	 * 1.27.0: the settings of the block as the designer edits them, a JSON object in the vocabulary of its own blocks
	 * (LAYOUT-FORMAT.md, "Registered components"): {@code {"def": {initial keys}, "spec": [controls]}}; null: the
	 * inspector shows the component's name only.
	 */
	default String designerSpec()
	{
		return null;
	}

	/** 1.27.0: the panel's title when the block has none; null for none. */
	default String title(final JsonNode pBlock, final Locale pLocale)
	{
		return null;
	}
}
