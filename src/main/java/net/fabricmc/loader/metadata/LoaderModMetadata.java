package net.fabricmc.loader.metadata;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.Collection;

/**
 * Internal variant of the ModMetadata interface.
 */
public interface LoaderModMetadata extends ModMetadata {
	String getLanguageAdapter();
	Collection<String> getInitializers();
	Collection<String> getMixinConfigs(EnvType type);
	boolean matchesEnvironment(EnvType type);
}
