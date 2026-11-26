package com.on.ebx.layout.designer;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.onwbp.base.text.Severity;
import com.onwbp.base.text.UserMessage;

/** The module's own texts ({@code Messages*.properties} of this package), English when a locale has none. */
public final class Messages
{
	private static final String BUNDLE = Messages.class.getPackage().getName() + ".Messages";

	private Messages()
	{
	}

	public static String get(final Locale pLocale, final String pKey, final Object... pArgs)
	{
		final Locale locale = pLocale == null ? Locale.ENGLISH : pLocale;
		String pattern;
		try
		{
			pattern = ResourceBundle.getBundle(Messages.BUNDLE, locale, Messages.class.getClassLoader()).getString(pKey);
		}
		catch (final MissingResourceException ex)
		{
			return pKey;
		}
		if (pArgs == null || pArgs.length == 0)
		{
			return pattern;
		}
		try
		{
			return new MessageFormat(pattern, locale).format(pArgs);
		}
		catch (final IllegalArgumentException ex)
		{
			return pattern;
		}
	}

	public static UserMessage info(final String pKey, final Object... pArgs)
	{
		return new Localized(pKey, pArgs, Severity.INFO);
	}

	static final class Localized extends UserMessage
	{
		private static final long serialVersionUID = 1L;
		private final String key;
		private final Object[] args;

		Localized(final String pKey, final Object[] pArgs, final Severity pSeverity)
		{
			super(pSeverity, pArgs == null ? new Object[0] : pArgs);
			this.key = pKey;
			this.args = pArgs == null ? new Object[0] : pArgs;
		}

		@Override
		protected String lookupRawString(final Locale pLocale)
		{
			return Messages.get(pLocale, this.key, this.args);
		}

		@Override
		protected String getRawStringOrNull(final Locale pLocale)
		{
			return Messages.get(pLocale, this.key, this.args);
		}

		@Override
		public String getInternalId()
		{
			return Messages.BUNDLE + "#" + this.key;
		}

		@Override
		protected UserMessage copy(final Object[] pArgs)
		{
			return new Localized(this.key, pArgs, this.getSeverity());
		}
	}
}
