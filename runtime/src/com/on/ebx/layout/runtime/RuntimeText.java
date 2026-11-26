package com.on.ebx.layout.runtime;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The few words the rendered form writes itself (not configured in a layout). They live in the bundle
 * {@code RuntimeText.properties} (English, the base), {@code RuntimeText_fr.properties} and {@code RuntimeText_it.properties}
 * next to this class in the runtime jar; a language with no file, or a key missing from a language file, reads English
 * (audit L2-21). Configured texts never come from here. A new visible text is a new key in the three files, never a literal
 * in the code ({@code RuntimeTextTest} refuses one).
 */
public final class RuntimeText
{
	private static final String BASE = "com.on.ebx.layout.runtime.RuntimeText";
	/** One bundle per language, kept (a bundle is immutable once read). */
	private static final Map<String, ResourceBundle> BUNDLES = new HashMap<String, ResourceBundle>();

	private RuntimeText()
	{
	}

	private static synchronized ResourceBundle bundle(final Locale pLocale)
	{
		final String lang = pLocale == null ? "" : pLocale.getLanguage();
		ResourceBundle b = RuntimeText.BUNDLES.get(lang);
		if (b == null)
		{
			// no fallback to the JVM's default locale: a language without a file reads the base (English), not the server's language
			b = ResourceBundle.getBundle(RuntimeText.BASE, new Locale(lang), RuntimeText.class.getClassLoader(),
				ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
			RuntimeText.BUNDLES.put(lang, b);
		}
		return b;
	}

	public static String get(final String pKey, final Locale pLocale)
	{
		try
		{
			return RuntimeText.bundle(pLocale).getString(pKey);
		}
		catch (final MissingResourceException ex)
		{
			return pKey;
		}
	}

	public static String get(final String pKey, final Locale pLocale, final Object pN)
	{
		return RuntimeText.get(pKey, pLocale).replace("{n}", String.valueOf(pN));
	}
}
