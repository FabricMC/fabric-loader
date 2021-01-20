package net.fabricmc.loader.api.config;

import net.fabricmc.loader.api.config.value.ValueContainerProvider;

import java.util.function.Function;

/**
 * This is called after configs have been (de)serialized for the first time.
 *
 * <p>This is the appropriate entrypoint to use to call {@link ValueContainerProvider#register(Function)}.</p>
 *
 * <p>The entrypoint is exposed with {@code configsLoaded} key in the mod json and runs for any environment. It is run
 * immediately before any {@link net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint}s.</p>
 */
public interface ConfigsLoadedEntrypoint {
	void onConfigsLoaded();
}
