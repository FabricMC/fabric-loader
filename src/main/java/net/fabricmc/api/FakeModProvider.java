package net.fabricmc.api;

import net.fabricmc.loader.api.ModContainer;

import java.util.Collection;

/**
 * Hook for adding "fake" mod containers to Loader's list.
 * Should run <i>after</i> {@link ModInitializer#onInitialize()}
 */
public interface FakeModProvider {
	Collection<ModContainer> getFakeMods();
}
