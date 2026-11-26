package com.on.ebx.layout.designer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.orchestranetworks.instance.Repository;

/**
 * The build of this module ({@code version.properties}, written by {@code build.sh}) and the ON EBX it runs on. Every
 * value reads "unknown" when it cannot be found: a build without the stamp still opens its About page.
 */
public final class ModuleVersion
{
	public static final String UNKNOWN = "unknown";
	static final String RESOURCE = "version.properties";

	private static Properties stamp;

	private ModuleVersion()
	{
	}

	public static String version()
	{
		return ModuleVersion.get("version");
	}

	public static String commit()
	{
		return ModuleVersion.get("commit");
	}

	/** UTC, ISO-8601 ({@code 2026-10-07T09:12:00Z}). */
	public static String builtAt()
	{
		return ModuleVersion.get("builtAt");
	}

	/** True when the build wrote its stamp. */
	public static boolean isStamped()
	{
		return !ModuleVersion.properties().isEmpty();
	}

	/** The running ON EBX: the implementation version of its jar, with the product build number when there is one. */
	public static String ebx()
	{
		try
		{
			final Package p = Repository.class.getPackage();
			final String version = p == null ? null : p.getImplementationVersion();
			return version == null || version.trim().isEmpty() ? ModuleVersion.UNKNOWN : version;
		}
		catch (final RuntimeException ex)
		{
			return ModuleVersion.UNKNOWN;
		}
	}

	/** The line a user pastes in a support request. */
	public static String supportLine()
	{
		return "ON EBX Layout Designer " + ModuleVersion.version() + " (" + ModuleVersion.commit() + ", built " + ModuleVersion.builtAt()
			+ "; ON EBX " + ModuleVersion.ebx() + ")";
	}

	private static String get(final String pKey)
	{
		final String value = ModuleVersion.properties().getProperty(pKey);
		return value == null || value.trim().isEmpty() ? ModuleVersion.UNKNOWN : value.trim();
	}

	private static synchronized Properties properties()
	{
		if (ModuleVersion.stamp == null)
		{
			final Properties p = new Properties();
			try (InputStream in = ModuleVersion.class.getResourceAsStream(ModuleVersion.RESOURCE))
			{
				if (in != null)
				{
					p.load(in);
				}
			}
			catch (final IOException ex)
			{
				// no stamp: every value reads "unknown"
			}
			ModuleVersion.stamp = p;
		}
		return ModuleVersion.stamp;
	}
}
