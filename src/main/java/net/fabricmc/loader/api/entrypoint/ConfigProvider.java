package net.fabricmc.loader.api.entrypoint;

import java.util.function.BiConsumer;

/**
 * Allows the definition of multiple config files with one entrypoint.
 * See {@link ConfigInitializer}
 */
public interface ConfigProvider {
	/**
	 * @param consumer consumes the modId under which to register a config file and the initializer to create it
	 */
	void addConfigs(BiConsumer<String, ConfigInitializer> consumer);
}
