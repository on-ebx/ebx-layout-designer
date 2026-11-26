package com.on.ebx.layout.runtime;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.orchestranetworks.ui.form.UIFormContext;
import com.orchestranetworks.ui.form.UIFormPaneWriter;

/**
 * A block a module writes in Java and registers by name ({@link LayoutComponents#register}), so that the designer
 * can place it like any other block. The component receives its block as configured (title, note, parameters) and
 * writes inside the panel the runtime has opened for it.
 */
public interface LayoutComponent
{
	/** Shown in the designer's palette. */
	String getLabel(Locale pLocale);

	void write(UIFormPaneWriter pWriter, UIFormContext pContext, JsonNode pBlock, Locale pLocale);
}
