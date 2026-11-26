package com.on.ebx.layout.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The components registered by name. This class lives in the runtime jar, on the class path every module shares,
 * so a module registers its components from its own {@code ModuleRegistration} (at repository startup) and the
 * form of any model finds them. A module that stops must unregister what it registered.
 */
public final class LayoutComponents
{
	private static final Map<String, LayoutComponent> COMPONENTS = new ConcurrentHashMap<String, LayoutComponent>();

	private LayoutComponents()
	{
	}

	public static void register(final String pName, final LayoutComponent pComponent)
	{
		LayoutComponents.COMPONENTS.put(pName, pComponent);
	}

	public static void unregister(final String pName)
	{
		LayoutComponents.COMPONENTS.remove(pName);
	}

	public static LayoutComponent get(final String pName)
	{
		return pName == null ? null : LayoutComponents.COMPONENTS.get(pName);
	}

	public static Map<String, LayoutComponent> all()
	{
		return Collections.unmodifiableMap(new TreeMap<String, LayoutComponent>(LayoutComponents.COMPONENTS));
	}
}
