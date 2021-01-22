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

package net.fabricmc.loader.api.config;

import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.config.ConfigManagerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ConfigManager {
	Logger LOGGER = LogManager.getLogger("Fabric|Config");

	/**
	 * @return all registered config definitions
	 */
	static Collection<ConfigDefinition> getConfigKeys() {
		return ConfigManagerImpl.getConfigKeys();
	}

	/**
	 * @param configDefinition the config file whose values we want
	 * @return a list of value keys associated with the config file
	 */
	static Collection<ValueKey<?>> getValues(ConfigDefinition configDefinition) {
		return ConfigManagerImpl.getValues(configDefinition);
	}

	/**
	 * @param configKeyString the path of a config value key
	 * @return the value key associated with that path if it exists, null otherwise
	 */
	static @Nullable ValueKey<?> getValue(String configKeyString) {
		return ConfigManagerImpl.getValue(configKeyString);
	}

	/**
	 * Saves the config definition to disk with values from the specified value container.
	 *
	 * @param config the config file to save
	 * @param valueContainer the value container where values are stored
	 */
	static void doSerialization(ConfigDefinition config, ValueContainer valueContainer) {
		ConfigManagerImpl.doSerialization(config, valueContainer);
	}

	/**
	 * @param configKeyString the path of a config definition
	 * @return the config definition if it exists, null otherwise
	 */
	static @Nullable ConfigDefinition getDefinition(String configKeyString) {
		return ConfigManagerImpl.getDefinition(configKeyString);
	}
}
