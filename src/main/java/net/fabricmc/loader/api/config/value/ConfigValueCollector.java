package net.fabricmc.loader.api.config.value;

import org.jetbrains.annotations.NotNull;

public interface ConfigValueCollector {
	/**
	 * Adds a config value to this builder
	 * @param configValue the value to add
	 * @param path0 the first element of the path
	 * @param path any additional elements of the path, for nested elements
	 */
    void addConfigValue(@NotNull ConfigValue<?> configValue, @NotNull String path0, String... path);
}
