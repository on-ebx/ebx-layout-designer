package com.on.ebx.layout.designer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.on.ebx.layout.runtime.LayoutPaths;
import com.on.ebx.layout.runtime.LayoutStore;
import com.onwbp.adaptation.Adaptation;
import com.onwbp.adaptation.AdaptationHome;
import com.onwbp.adaptation.AdaptationName;
import com.onwbp.adaptation.AdaptationReference;
import com.onwbp.base.text.UserMessage;
import com.orchestranetworks.instance.HomeCreationSpec;
import com.orchestranetworks.instance.HomeKey;
import com.orchestranetworks.instance.Repository;
import com.orchestranetworks.schema.Path;
import com.orchestranetworks.schema.SchemaLocation;
import com.orchestranetworks.service.LoggingCategory;
import com.orchestranetworks.service.Procedure;
import com.orchestranetworks.service.ProcedureContext;
import com.orchestranetworks.service.ProcedureResult;
import com.orchestranetworks.service.Profile;
import com.orchestranetworks.service.ProgrammaticService;
import com.orchestranetworks.service.Session;

/**
 * Creates the module's dataspace and dataset at the first start, then loads the layouts shipped with the module
 * ({@code /seeds/index.txt} lists them) for any (model, table) that has no layout yet. Never overwrites a layout.
 */
final class Installer
{
	private Installer()
	{
	}

	static void install(final Repository pRepository, final Session pSession, final String pModuleName, final LoggingCategory pLog)
	{
		try
		{
			AdaptationHome home = pRepository.lookupHome(HomeKey.forBranchName(LayoutPaths.DATASPACE));
			if (home == null)
			{
				final HomeCreationSpec spec = new HomeCreationSpec();
				spec.setKey(HomeKey.forBranchName(LayoutPaths.DATASPACE));
				spec.setParent(pRepository.getReferenceBranch());
				spec.setOwner(Profile.ADMINISTRATOR);
				spec.setLabel(UserMessage.createInfo("Layout Designer"));
				spec.setDescription(UserMessage.createInfo("Record form layouts drawn in the Layout Designer."));
				home = pRepository.createHome(spec, pSession);
				pLog.info("[layout-designer] dataspace " + LayoutPaths.DATASPACE + " created");
			}
			Adaptation dataset = home.findAdaptationOrNull(AdaptationName.forName(LayoutPaths.DATASET));
			if (dataset == null)
			{
				final ProcedureResult result = ProgrammaticService.createForSession(pSession, home).execute(new Procedure()
				{
					@Override
					public void execute(final ProcedureContext pContext) throws Exception
					{
						pContext.setAllPrivileges(true);
						final Adaptation created = pContext.doCreateRoot(SchemaLocation.forPathInModule(LayoutPaths.MODEL_PATH, pModuleName),
							AdaptationReference.forPersistentName(LayoutPaths.DATASET), Profile.ADMINISTRATOR);
						pContext.setInstanceLabel(created, UserMessage.createInfo("Layout Designer"));
					}
				});
				if (result.hasFailed())
				{
					pLog.error("[layout-designer] dataset creation failed: " + result.getExceptionFullMessage(Locale.ENGLISH));
					return;
				}
				pLog.info("[layout-designer] dataset " + LayoutPaths.DATASET + " created");
			}
			Installer.seed(pRepository, pSession, pLog);
		}
		catch (final Exception ex)
		{
			pLog.error("[layout-designer] installation failed: " + ex, ex);
		}
	}

	private static void seed(final Repository pRepository, final Session pSession, final LoggingCategory pLog)
	{
		final String index = Installer.resource("/seeds/index.txt");
		if (index == null)
		{
			return;
		}
		for (final String line : index.split("\\R"))
		{
			final String name = line.trim();
			if (name.isEmpty() || name.startsWith("#"))
			{
				continue;
			}
			try
			{
				final String text = Installer.resource("/seeds/" + name);
				if (text == null)
				{
					continue;
				}
				final JsonNode layout = LayoutStore.json().readTree(text);
				final SchemaLocation model = SchemaLocation.parse(layout.path("model").asText());
				final Path table = Path.parse(layout.path("table").asText());
				if (LayoutStore.findRow(pRepository, model, table) != null)
				{
					continue;
				}
				LayoutWriter.save(pRepository, pSession, model, table, layout.path("name").asText(name), text, null);
				pLog.info("[layout-designer] layout " + name + " loaded for " + model.format() + " " + table.format());
			}
			catch (final Exception ex)
			{
				pLog.error("[layout-designer] layout " + name + " not loaded: " + ex, ex);
			}
		}
	}

	private static String resource(final String pPath)
	{
		try (InputStream in = Installer.class.getResourceAsStream(pPath))
		{
			if (in == null)
			{
				return null;
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			for (int n = in.read(buffer); n > 0; n = in.read(buffer))
			{
				out.write(buffer, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
		catch (final Exception ex)
		{
			return null;
		}
	}
}
