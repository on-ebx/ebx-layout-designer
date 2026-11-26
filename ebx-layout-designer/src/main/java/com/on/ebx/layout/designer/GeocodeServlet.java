package com.on.ebx.layout.designer;

import java.io.IOException;
import java.util.Locale;

import com.on.ebx.layout.runtime.GeoTicket;
import com.on.ebx.layout.runtime.Geocoding;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /geocode?q=address&t=ticket: the position of one address, for the "Locate now" button of a map block (1.15.1).
 * Answers {"lat":…, "lon":…} or {"found":false}; 403 without a valid ticket from a form of this server. The service is
 * asked at its pace (one request a second, server-wide), so a call may wait.
 */
@WebServlet(urlPatterns = "/geocode")
public final class GeocodeServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	private static final int MAX_LENGTH = 300;

	@Override
	protected void doGet(final HttpServletRequest pRequest, final HttpServletResponse pResponse) throws IOException
	{
		pResponse.setHeader("Cache-Control", "no-store");
		if (!GeoTicket.valid(pRequest.getParameter("t")))
		{
			pResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		final String q = pRequest.getParameter("q");
		if (q == null || q.trim().isEmpty() || q.length() > GeocodeServlet.MAX_LENGTH)
		{
			pResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		final Locale locale = pRequest.getLocale() == null ? Locale.ENGLISH : pRequest.getLocale();
		final double[] at = Geocoding.locateNow(q, locale);
		pResponse.setContentType("application/json;charset=UTF-8");
		pResponse.getWriter().write(at == null ? "{\"found\":false}" : "{\"lat\":" + at[0] + ",\"lon\":" + at[1] + "}");
	}
}
