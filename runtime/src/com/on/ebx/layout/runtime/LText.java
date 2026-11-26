package com.on.ebx.layout.runtime;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A configured text of a layout is an object with one entry per locale of the instance ({@code {"en-US": "...",
 * "fr-FR": "..."}}). The shown text is the one of the user's locale, else one of the same language, else the one
 * of the instance's default locale, else the first one written.
 */
public final class LText
{
	private LText()
	{
	}

	public static String pick(final JsonNode pText, final Locale pLocale, final Locale pDefault)
	{
		if (pText == null || pText.isNull() || pText.isMissingNode())
		{
			return "";
		}
		if (pText.isTextual())
		{
			return pText.asText();
		}
		if (!pText.isObject())
		{
			return "";
		}
		final String exact = LText.text(pText, pLocale == null ? null : pLocale.toLanguageTag());
		if (!exact.isEmpty())
		{
			return exact;
		}
		if (pLocale != null)
		{
			final Iterator<Map.Entry<String, JsonNode>> it = pText.fields();
			while (it.hasNext())
			{
				final Map.Entry<String, JsonNode> e = it.next();
				if (Locale.forLanguageTag(e.getKey()).getLanguage().equals(pLocale.getLanguage()) && !e.getValue().asText("").isEmpty())
				{
					return e.getValue().asText();
				}
			}
		}
		final String byDefault = LText.text(pText, pDefault == null ? null : pDefault.toLanguageTag());
		if (!byDefault.isEmpty())
		{
			return byDefault;
		}
		final Iterator<JsonNode> values = pText.elements();
		while (values.hasNext())
		{
			final String v = values.next().asText("");
			if (!v.isEmpty())
			{
				return v;
			}
		}
		return "";
	}

	public static boolean hasText(final JsonNode pText)
	{
		if (pText == null || pText.isNull() || pText.isMissingNode())
		{
			return false;
		}
		if (pText.isTextual())
		{
			return !pText.asText().isEmpty();
		}
		final Iterator<JsonNode> values = pText.elements();
		while (values.hasNext())
		{
			if (!values.next().asText("").isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	private static String text(final JsonNode pText, final String pTag)
	{
		if (pTag == null)
		{
			return "";
		}
		final JsonNode v = pText.get(pTag);
		return v == null ? "" : v.asText("");
	}
}
