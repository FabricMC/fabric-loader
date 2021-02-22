/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.api.config.value;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.SaveType;
import net.fabricmc.loader.config.ConfigManagerImpl;
import net.fabricmc.loader.config.ValueContainerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface ValueContainer {
    ValueContainer ROOT = new ValueContainerImpl(FabricLoader.getInstance().getConfigDir().normalize(), SaveType.ROOT);

	static ValueContainer of(Path saveDirectory, SaveType... saveTypes) {
		ValueContainer valueContainer = new ValueContainerImpl(saveDirectory, saveTypes);

		if (saveDirectory != null) {
			for (ConfigDefinition<?> config : ConfigManager.getConfigKeys()) {
				if (valueContainer.contains(config.getSaveType())) {
					ConfigManagerImpl.doSerialization(config, valueContainer);
				}
			}
		}

		return valueContainer;
	}

	/**
     * Puts the specified value into this value container.
     * @param valueKey the key of the value to store
     * @param newValue the actual value to store
     * @param <T> the type of the actual value
     * @return the value previously stored in the ValueContainer, or the default value
     */
	@ApiStatus.Internal
    <T> T put(@NotNull ValueKey<T> valueKey, @NotNull T newValue);

    /**
     * Gets the stored value of the specified config key stored in this container.
     * @param valueKey the key of the value to fetch
     * @param <T> the type of the actual value
     * @return the value stored in the ValueContainer, or the default value
     */
    @ApiStatus.Internal
    <T> T get(ValueKey<T> valueKey);

    /**
     * Gets the number of values belonging to the specified config key that have unsaved modifications.
     * @param configDefinition the config file in question
     * @return the number of unsaved modified config values
     */
    int countUnsavedChanges(ConfigDefinition<?> configDefinition);

    /**
     * Determines whether or not the specified config file has unsaved changes.
     * @param configDefinition the config file in question
     * @return whether or not the config file has changes that need to be saved
     */
    default boolean hasUnsavedChanges(ConfigDefinition<?> configDefinition) {
    	return this.countUnsavedChanges(configDefinition) > 0;
	}

    /**
     * Saves the specified config file to disk.
     * @param configDefinition the config file in question
     */
    void save(ConfigDefinition<?> configDefinition);

	/**
	 * @param saveType the save type to check
	 * @return whether or not this container contains configs of the specified type
	 */
    boolean contains(SaveType saveType);

	/**
	 * @return the directory this value container saves configs to
	 */
    Path getSaveDirectory();
}
