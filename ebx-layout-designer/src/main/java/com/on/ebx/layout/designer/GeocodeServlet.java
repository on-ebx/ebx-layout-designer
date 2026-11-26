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
 * Answers {"lat":…, "lon":…} or {"found":false} at once when the cache knows, 202 {"pending":true} when the address is queued
 * for the background thread (the page asks again in two seconds), 429 {"error":"LDG-429"…} when that queue is full; 403
 * without a valid ticket from a form of this server. The request never waits for the geocoding service (1.23.3, L2-10).
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
		final Geocoding.Result r = Geocoding.locateNow(q, locale);
		pResponse.setContentType("application/json;charset=UTF-8");
		switch (r.state)
		{
			case FOUND:
				pResponse.getWriter().write("{\"lat\":" + r.at[0] + ",\"lon\":" + r.at[1] + "}");
				break;
			case PENDING:
				pResponse.setStatus(HttpServletResponse.SC_ACCEPTED);
				pResponse.getWriter().write("{\"pending\":true}");
				break;
			case REFUSED:
				pResponse.setStatus(429);
				pResponse.setHeader("Retry-After", "120");
				pResponse.getWriter().write("{\"found\":false,\"error\":\"LDG-429\",\"message\":\"The geocoding queue is full (code LDG-429): try again in a few minutes.\"}");
				break;
			default:
				pResponse.getWriter().write("{\"found\":false}");
		}
	}
}
