package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.SaveType;
import net.fabricmc.loader.api.config.data.DataCollector;
import net.fabricmc.loader.api.config.value.ConfigValueCollector;
import org.jetbrains.annotations.NotNull;

/**
 * Represents one config file.
 * See {@link ConfigProvider}
 */
public interface ConfigInitializer {
	/**
	 * @return the concrete serializer instance associated with this config file.
	 */
    @NotNull ConfigSerializer getSerializer();

    @NotNull SaveType getSaveType();

	/**
	 * Adds all config values belonging to this file to the supplied {@link ConfigValueCollector}.
	 * @param builder the builder to add to
	 */
    void addConfigValues(@NotNull ConfigValueCollector builder);

	/**
	 * Used to add data (such as comments) to a config file.
	 * @param collector collects data to be added to the config file
	 */
    default void addConfigData(@NotNull DataCollector collector) {

	}

	/**
	 * @return the name of the file (minus extension) that this config file will be saved as
	 */
    default @NotNull String getName() {
    	return "config";
	}

	/**
	 * @return an array of folder names that point where this config file will be saved, relative to 'config/namespace'
	 */
	default @NotNull String[] getSavePath() {
        return new String[0];
    }

	/**
	 * @return the version of this config file, used for backing up and migrating from old configs
	 */
    default @NotNull SemanticVersion getVersion() {
    	return SemanticVersion.of(1, 0, 0);
	}
}
