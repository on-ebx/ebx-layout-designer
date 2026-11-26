package com.on.ebx.layout.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.onwbp.adaptation.Adaptation;

/**
 * Values a module computes for the explorers, by name: a computed value {@code {"type": "provided", "name": "…"}} of an
 * explorer reads them. This class lives in the runtime jar, on the class path every module shares, so a module registers
 * its providers from its own {@code ModuleRegistration} and unregisters them when it stops. A provider is called once per
 * record when the explorer's index is built, never per card shown.
 */
public final class ExplorerValues
{
	/** One value per record: a String, a Number, a Boolean, a java.util.Date, a java.util.List of these, or null. */
	public interface Provider
	{
		Object value(Adaptation pRecord);
	}

	private static final Map<String, Provider> PROVIDERS = new ConcurrentHashMap<String, Provider>();

	private ExplorerValues()
	{
	}

	public static void register(final String pName, final Provider pProvider)
	{
		ExplorerValues.PROVIDERS.put(pName, pProvider);
	}

	public static void unregister(final String pName)
	{
		ExplorerValues.PROVIDERS.remove(pName);
	}

	public static Provider get(final String pName)
	{
		return pName == null ? null : ExplorerValues.PROVIDERS.get(pName);
	}

	public static java.util.Set<String> names()
	{
		return new java.util.TreeSet<String>(ExplorerValues.PROVIDERS.keySet());
	}
}
