package net.fabricmc.loader.api.config.value;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.SaveType;
import net.fabricmc.loader.config.ValueContainerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface ValueContainer {
    ValueContainer ROOT = new ValueContainerImpl(FabricLoader.getInstance().getConfigDir().normalize());

	static ValueContainer of(Path saveDirectory, SaveType... saveTypes) {
		ValueContainer valueContainer = new ValueContainerImpl(saveDirectory, saveTypes);

		if (saveDirectory != null) {
			for (ConfigDefinition config : ConfigManager.getConfigKeys()) {
				if (valueContainer.contains(config.getSaveType())) {
					ConfigManager.doSerialization(config, valueContainer);
				}
			}
		}

		return valueContainer;
	}

	/**
     * Puts the specified value into this value container.
     * @param configValue the config value to store
     * @param newValue the actual value to store
     * @param <T> the type of the actual value
     * @return the value previously stored in the ValueContainer, or the default value
     */
	@ApiStatus.Internal
    <T> T put(@NotNull ConfigValue<T> configValue, @NotNull T newValue);

    /**
     * Gets the stored value of the specified config value stored in this container.
     * @param configValue the config value to fetch
     * @param <T> the type of the actual value
     * @return the value stored in the ValueContainer, or the default value
     */
    @ApiStatus.Internal
    <T> T get(ConfigValue<T> configValue);

    /**
     * Gets the number of values belonging to the specified config key that have unsaved modifications.
     * @param configDefinition the config file in question
     * @return the number of unsaved modified config values
     */
    int countUnsavedChanges(ConfigDefinition configDefinition);

    /**
     * Determines whether or not the specified config file has unsaved changes.
     * @param configDefinition the config file in question
     * @return whether or not the config file has changes that need to be saved
     */
    boolean hasUnsavedChanges(ConfigDefinition configDefinition);

    /**
     * Saves the specified config file to disk.
     * @param configDefinition the config file in question
     */
    void save(ConfigDefinition configDefinition);

    boolean contains(SaveType saveType);

    Path getSaveDirectory();
}
