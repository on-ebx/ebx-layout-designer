package com.on.ebx.layout.runtime;

/**
 * Whether the style sheet and the scripts shared by every pane of a form were already written in this display (audit
 * L2-17): the first pane written takes them, the others (one per tab, all in the same page) leave them out.
 */
final class FormAssets
{
	private boolean taken;

	/** True the first time it is called, false afterwards. */
	boolean first()
	{
		if (this.taken)
		{
			return false;
		}
		this.taken = true;
		return true;
	}
}
