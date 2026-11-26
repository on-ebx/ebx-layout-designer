package com.on.ebx.layout.runtime;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onwbp.adaptation.Adaptation;

/**
 * The "Sites on a map" block: the rows of an association (establishments, addresses) on an OpenStreetMap map, with the
 * list beside it. A row is placed by its latitude and longitude, else by its address through {@link Geocoder}: an address
 * not known yet is looked up in the background (the public service takes one a second) and placed at the next display;
 * the form never waits for the service. Drawn in the browser by {@code blocks.js} with OpenStreetMap tiles.
 */
final class SitesMap
{
	static final int MAX_ROWS = 200;

	private SitesMap()
	{
	}

	static String render(final RenderContext rc, final JsonNode b, final String pId)
	{
		final String a = b.path("a").asText("");
		if (a.isEmpty())
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("setupBlock", rc.locale)) + "</div>";
		}
		if (rc.creating || rc.record == null)
		{
			return "<div class=\"ldf-more\">" + LayoutPane.esc(RuntimeText.get("afterCreate", rc.locale)) + "</div>";
		}
		final String lat = b.path("lat").asText("");
		final String lng = b.path("lng").asText("");
		final String label = b.path("label").asText("");
		final String hq = b.path("hq").asText("");
		final String active = b.path("active").asText("");
		final boolean showInactive = b.path("showInactive").asBoolean(false);
		final List<String> address = new ArrayList<String>();
		for (final JsonNode p : b.path("address"))
		{
			address.add(p.asText());
		}
		final String detail = b.path("detail").asText("");
		final ObjectNode data = LayoutStore.json().createObjectNode();
		data.put("tall", b.path("tall").asBoolean(false));
		final ObjectNode t = data.putObject("t");
		for (final String k : new String[] { "hq", "approx", "pending", "notFound", "sites", "openMap", "zoomIn", "zoomOut", "open", "inactive" })
		{
			t.put(k, RuntimeText.get("sm." + k, rc.locale));
		}
		final ArrayNode sites = data.putArray("sites");
		boolean cut = false;
		final List<Adaptation> rows = rc.rows(a, SitesMap.MAX_ROWS + 1);
		for (final Adaptation r : rows)
		{
			if (sites.size() >= SitesMap.MAX_ROWS)
			{
				cut = true;
				break;
			}
			final boolean isActive = active.isEmpty() || !Boolean.FALSE.equals(Values.raw(r, active));
			if (!isActive && !showInactive)
			{
				continue;
			}
			final ObjectNode s = sites.addObject();
			final List<String> parts = new ArrayList<String>();
			for (final String p : address)
			{
				final String v = Values.display(r, p, rc.locale).trim();
				if (!v.isEmpty())
				{
					parts.add(v);
				}
			}
			final String adr = String.join(", ", parts);
			final String name = label.isEmpty() ? "" : Values.display(r, label, rc.locale);
			s.put("n", name.isEmpty() ? r.getLabel(rc.locale) : name);
			s.put("a", adr);
			if (!detail.isEmpty())
			{
				s.put("d", Values.display(r, detail, rc.locale));
			}
			s.put("hq", !hq.isEmpty() && Boolean.TRUE.equals(Values.raw(r, hq)));
			s.put("off", !isActive);
			s.put("u", MoreBlocks.recordUrl(rc, r));
			final Double la = lat.isEmpty() ? null : Values.number(Values.raw(r, lat));
			final Double lo = lng.isEmpty() ? null : Values.number(Values.raw(r, lng));
			if (la != null && lo != null)
			{
				s.put("lat", la.doubleValue());
				s.put("lon", lo.doubleValue());
				continue;
			}
			if (adr.isEmpty())
			{
				s.put("miss", true);
				continue;
			}
			// never wait for the geocoding service while the form is drawn (1.15.0): known positions now, the others next time
			double[] at = Geocoder.known(adr);
			boolean pending = false;
			if (at == null && !Geocoder.isKnown(adr))
			{
				Geocoder.lookLater(adr, rc.locale);
				pending = true;
			}
			if (at == null && !pending && parts.size() > 1)
			{
				// the street may be unknown to the map: the town (the last parts) gives an approximate position
				final String town = String.join(", ", parts.subList(Math.max(0, parts.size() - (address.size() > 2 ? 2 : 1)), parts.size()));
				at = Geocoder.known(town);
				if (at != null)
				{
					s.put("approx", true);
				}
				else if (!Geocoder.isKnown(town))
				{
					Geocoder.lookLater(town, rc.locale);
					pending = true;
				}
			}
			if (at == null && pending)
			{
				s.put("pending", true);
				continue;
			}
			if (at == null)
			{
				s.put("miss", true);
				continue;
			}
			s.put("lat", at[0]);
			s.put("lon", at[1]);
		}
		data.put("cut", cut);
		final String json = data.toString().replace("</", "<\\/");
		return "<div class=\"ldf-sm\" data-sm=\"" + LayoutPane.esc(pId) + "\"><div class=\"ldf-sm-body\"></div><script type=\"application/json\">" + json + "</script></div>";
	}
}
