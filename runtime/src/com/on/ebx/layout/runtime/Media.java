package com.on.ebx.layout.runtime;

import java.lang.reflect.Method;
import java.util.Locale;

import com.onwbp.adaptation.Adaptation;
import com.orchestranetworks.instance.Repository;

/**
 * The address of an image or a file held by a field: a text that is already a URL, or a media field of the Digital
 * Asset Manager add-on, read through its public API ({@code MediaType.getAttachment()},
 * {@code AssetManager.getDigitalAsset(uuid)}, {@code DigitalAsset.getURL()}, all in the add-on's Javadoc). The add-on
 * is called by reflection so that a form works on an instance without it.
 */
final class Media
{
	static final class Asset
	{
		final String url;
		final String name;
		final String extension;

		Asset(final String pUrl, final String pName, final String pExtension)
		{
			this.url = pUrl;
			this.name = pName;
			this.extension = pExtension;
		}

		boolean isImage()
		{
			final String e = (this.extension == null ? Media.extension(this.url) : this.extension).toLowerCase(Locale.ROOT);
			return e.matches("png|jpe?g|gif|webp|svg|bmp|avif");
		}
	}

	private Media()
	{
	}

	/** The asset a value points to, or null when the value is empty or unreadable. */
	static Asset of(final Adaptation pRow, final Object pValue, final Locale pLocale)
	{
		if (pValue == null)
		{
			return null;
		}
		if (pValue instanceof String)
		{
			final String s = ((String) pValue).trim();
			if (s.matches("(?i)^(https?://|/).+"))
			{
				final String name = s.replaceAll("[?#].*$", "").replaceAll("^.*/", "");
				return new Asset(s, name.isEmpty() ? s : name, Media.extension(s));
			}
			return null;
		}
		try
		{
			final Method attachment = pValue.getClass().getMethod("getAttachment");
			final Object uuid = attachment.invoke(pValue);
			if (uuid == null || String.valueOf(uuid).isEmpty())
			{
				return null;
			}
			final Repository repository = pRow == null ? Repository.getDefault() : pRow.getHome().getRepository();
			final Class<?> managerClass = Class.forName("com.orchestranetworks.addon.dama.ext.AssetManager");
			final Object manager = managerClass.getConstructor(Repository.class).newInstance(repository);
			final Object asset = managerClass.getMethod("getDigitalAsset", String.class).invoke(manager, String.valueOf(uuid));
			if (asset == null)
			{
				return null;
			}
			final String url = (String) asset.getClass().getMethod("getURL").invoke(asset);
			String name = null;
			try
			{
				final Object label = asset.getClass().getMethod("getLabel").invoke(asset);
				if (label != null)
				{
					name = (String) label.getClass().getMethod("formatMessage", Locale.class).invoke(label, pLocale);
				}
			}
			catch (final ReflectiveOperationException | RuntimeException ex)
			{
				// no label
			}
			if (name == null || name.isEmpty())
			{
				name = (String) asset.getClass().getMethod("getPhysicalName").invoke(asset);
			}
			String extension = null;
			try
			{
				extension = (String) asset.getClass().getMethod("getExtension").invoke(asset);
			}
			catch (final ReflectiveOperationException | RuntimeException ex)
			{
				// no extension
			}
			return url == null || url.isEmpty() ? null : new Asset(url, name == null ? "" : name, extension);
		}
		catch (final ReflectiveOperationException | RuntimeException | LinkageError ex)
		{
			return null;
		}
	}

	static String extension(final String pUrl)
	{
		if (pUrl == null)
		{
			return "";
		}
		final String s = pUrl.replaceAll("[?#].*$", "");
		final int dot = s.lastIndexOf('.');
		return dot < 0 || dot < s.lastIndexOf('/') ? "" : s.substring(dot + 1);
	}
}
