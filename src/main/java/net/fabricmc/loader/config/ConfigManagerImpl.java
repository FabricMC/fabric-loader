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

package net.fabricmc.loader.config;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.ConfigsLoadedEntrypoint;
import net.fabricmc.loader.api.config.data.DataCollector;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigSerializationException;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.entrypoint.ConfigInitializer;
import net.fabricmc.loader.api.entrypoint.ConfigProvider;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.entrypoint.minecraft.hooks.EntrypointUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManagerImpl {
    private static final Map<ConfigDefinition, Collection<ValueKey<?>>> CONFIGS = new HashMap<>();
    private static final Map<String, ConfigDefinition> CONFIG_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, ValueKey<?>> CONFIG_VALUES = new ConcurrentHashMap<>();

    private static boolean STARTED = false;
    private static boolean FINISHED = false;

    public static boolean isFinished() {
        return FINISHED;
    }

    public static Collection<ConfigDefinition> getConfigKeys() {
    	return CONFIGS.keySet();
	}

    public static Collection<ValueKey<?>> getValues(ConfigDefinition configDefinition) {
        return CONFIGS.getOrDefault(configDefinition, Collections.emptyList());
    }

    public static @Nullable ValueKey<?> getValue(String configKeyString) {
    	return CONFIG_VALUES.get(configKeyString);
	}

	public static @Nullable ConfigDefinition getDefinition(String configKeyString) {
		return CONFIG_DEFINITIONS.get(configKeyString);
	}

    public static void initialize() {
    	if (FINISHED || STARTED) return;
    	STARTED = true;

    	Map<String, Collection<ConfigInitializer>> configInitializers = new HashMap<>();

        for (EntrypointContainer<ConfigInitializer> container : FabricLoader.getInstance().getEntrypointContainers("config", ConfigInitializer.class)) {
            String modId = container.getProvider().getMetadata().getId();
            ConfigInitializer initializer = container.getEntrypoint();

            configInitializers.computeIfAbsent(modId, m -> new LinkedHashSet<>()).add(initializer);
        }

        for (EntrypointContainer<ConfigProvider> container : FabricLoader.getInstance().getEntrypointContainers("configProvider", ConfigProvider.class)) {
			container.getEntrypoint().addConfigs((modId, initializer) ->
					configInitializers.computeIfAbsent(modId, m -> new LinkedHashSet<>()).add(initializer));
		}

        for (String modId : configInitializers.keySet()) {
			for (ConfigInitializer initializer : configInitializers.get(modId)) {
				Map<DataType<?>, Collection<Object>> data = new HashMap<>();

				initializer.addConfigData(new DataCollector() {
					@SafeVarargs
					@Override
					public final <T> void add(DataType<T> type, T... values) {
						for (T object : values) {
							data.computeIfAbsent(type, t -> new ArrayList<>()).add(object);
						}
					}
				});

				ConfigDefinition configDefinition = new ConfigDefinition(modId, initializer.getName(), initializer.getSerializer(), initializer.getSaveType(), initializer.getVersion(), data, initializer.getSavePath());

				if (CONFIGS.containsKey(configDefinition)) {
					ConfigManager.LOGGER.warn("Attempted to register duplicate config '{}'", configDefinition.toString());
					continue;
				}

				initializer.addConfigValues(((configValue, path0, path) -> {
					configValue.initialize(configDefinition, path0, path);

					if (CONFIGS.getOrDefault(configDefinition, Collections.emptySet()).contains(configValue)) {
						ConfigManager.LOGGER.warn("Attempted to register duplicate config value '{}'", configValue);
						return;
					}

					CONFIGS.computeIfAbsent(configDefinition, c -> new ArrayList<>()).add(configValue);
					CONFIG_VALUES.put(configValue.toString(), configValue);
					CONFIG_DEFINITIONS.put(configDefinition.toString(), configDefinition);
				}));

				doSerialization(configDefinition, ValueContainer.ROOT);
			}
		}

        FINISHED = true;

		EntrypointUtils.invoke("configsLoaded", ConfigsLoadedEntrypoint.class, ConfigsLoadedEntrypoint::onConfigsLoaded);
    }

    public static void doSerialization(ConfigDefinition configDefinition, ValueContainer valueContainer) {
        ConfigSerializer serializer = configDefinition.getSerializer();

        try {
			SemanticVersion version = serializer.getVersion(configDefinition, valueContainer);

			if (version != null && configDefinition.getVersion().compareTo(version) != 0) {
				ConfigManager.LOGGER.warn("Expected config version '{}'. Found '{}'.", configDefinition.getVersion(), version);
				backup(valueContainer, configDefinition, serializer);
			}
		} catch (IOException | VersionParsingException e) {
        	ConfigManager.LOGGER.warn("Failed to get version from config '{}': {}", configDefinition, e.getMessage());
			backup(valueContainer, configDefinition, serializer);
		}

		try {
			if (Files.exists(serializer.getPath(configDefinition, valueContainer))) {
				boolean backup = serializer.deserialize(configDefinition, valueContainer);

				if (backup) {
					backup(valueContainer, configDefinition, serializer);
				}
			}
        } catch (IOException e) {
			ConfigManager.LOGGER.warn("Failed to deserialize config '{}': {}", configDefinition, e.getMessage());
			backup(valueContainer, configDefinition, serializer);
		}

		try {
			Path location = serializer.getPath(configDefinition, valueContainer);
			Files.createDirectories(location.getParent());
            serializer.serialize(configDefinition, valueContainer);
        } catch (IOException e) {
			throw new ConfigSerializationException(String.format("Failed to serialize config '%s': %s", configDefinition, e.getMessage()));
        }
	}

    private static void backup(ValueContainer valueContainer, ConfigDefinition configDefinition, ConfigSerializer serializer) {
		Path location = serializer.getPath(configDefinition, valueContainer);
		Path backupLocation = serializer.getPath(configDefinition, valueContainer, configDefinition.getVersion().getFriendlyString());

		ConfigManager.LOGGER.warn("Backing up config file at '{}' to '{}'", location.getFileName(), backupLocation.getFileName());

		try {
			Files.copy(location, backupLocation);
		} catch (IOException e) {
			throw new ConfigSerializationException(e);
		}
	}
}
