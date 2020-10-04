package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.ModContainer;

/**
 * Similar to {@link EntrypointContainer}, except it constructs the entrypoint when asked
 * instead of returning an already-held instance.
 */
public interface LazyEntrypointContainer<T> {
	/**
	 * Returns the entrypoint instance.
	 */
	T createEntrypoint() throws Exception;

	/**
	 * Returns the mod that provided this entrypoint.
	 */
	ModContainer getProvider();
}
