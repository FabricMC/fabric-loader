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
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.entrypoint.ConfigEnvironment;
import net.fabricmc.loader.api.config.entrypoint.ConfigPostInitializer;
import net.fabricmc.loader.api.config.data.DataCollector;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigSerializationException;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.config.entrypoint.ConfigInitializer;
import net.fabricmc.loader.api.config.entrypoint.ConfigProvider;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManagerImpl {
    private static final Map<ConfigDefinition<?>, Collection<ValueKey<?>>> CONFIGS = new HashMap<>();
    private static final Map<String, ConfigDefinition<?>> CONFIG_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, ValueKey<?>> CONFIG_VALUES = new ConcurrentHashMap<>();

    private static boolean FINISHED = false;

    public static boolean isFinished() {
        return FINISHED;
    }

    public static Collection<ConfigDefinition<?>> getConfigKeys() {
    	return CONFIGS.keySet();
	}

    public static Collection<ValueKey<?>> getValues(ConfigDefinition<?> configDefinition) {
        return CONFIGS.getOrDefault(configDefinition, Collections.emptyList());
    }

    public static @Nullable ValueKey<?> getValue(String configKeyString) {
    	return CONFIG_VALUES.get(configKeyString);
	}

	public static @Nullable ConfigDefinition<?> getDefinition(String configKeyString) {
		return CONFIG_DEFINITIONS.get(configKeyString);
	}

    public static void initialize() {
    	if (FINISHED) return;

    	Map<String, Collection<ConfigInitializer<?>>> configInitializers = new HashMap<>();
    	Collection<ConfigPostInitializer> postInitializers = new ArrayList<>();

    	for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("config", Object.class)) {
			Object entrypoint = container.getEntrypoint();

    		if (entrypoint instanceof ConfigEnvironment) {
				((ConfigEnvironment) entrypoint).addToRoot(((ValueContainerImpl) ValueContainer.ROOT)::add);
			}

    		if (entrypoint instanceof ConfigInitializer) {
				String modId = container.getProvider().getMetadata().getId();
				ConfigInitializer<?> initializer = (ConfigInitializer<?>) entrypoint;

				configInitializers.computeIfAbsent(modId, m -> new LinkedHashSet<>()).add(initializer);
			}

    		if (entrypoint instanceof ConfigProvider) {
				((ConfigProvider) entrypoint).addConfigs((modId, initializer) ->
						configInitializers.computeIfAbsent(modId, m -> new LinkedHashSet<>()).add(initializer));
			}

    		if (entrypoint instanceof ConfigPostInitializer) {
    			postInitializers.add((ConfigPostInitializer) entrypoint);
			}
		}

        for (String modId : configInitializers.keySet()) {
			//noinspection rawtypes
			for (ConfigInitializer initializer : configInitializers.get(modId)) {
				Map<DataType<?>, Collection<Object>> data = new HashMap<>();

				initializer.addConfigData(new DataCollector() {
					@Override
					public <T> void add(DataType<T> type, Collection<T> values) {
						data.computeIfAbsent(type, t -> new ArrayList<>()).addAll(values);
					}
				});

				//noinspection unchecked
				ConfigDefinition<?> configDefinition = new ConfigDefinition(modId, initializer.getName(), initializer.getVersion(), initializer.getSaveType(), data, initializer.getSerializer(), initializer::upgrade, initializer.getSavePath());

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
			}
		}

		postInitializers.forEach(ConfigPostInitializer::onConfigsLoaded);

        for (ConfigDefinition<?> configDefinition : CONFIG_DEFINITIONS.values()) {
        	doSerialization(configDefinition, ValueContainer.ROOT);
		}

        FINISHED = true;
    }

    public static <R> void doSerialization(ConfigDefinition<R> configDefinition, ValueContainer valueContainer) {
    	if (!valueContainer.contains(configDefinition.getSaveType())) return;

        ConfigSerializer<R> serializer = configDefinition.getSerializer();

        Path location = serializer.getPath(configDefinition, valueContainer);

		try {
			serializer.deserialize(configDefinition, valueContainer);
        } catch (IOException e) {
			throw new ConfigSerializationException(String.format("Failed to deserialize config '%s': %s", location, e.getMessage()));
		}

		save(configDefinition, valueContainer);
	}

	public static <R> void save(ConfigDefinition<R> configDefinition, ValueContainer valueContainer) {
		ConfigSerializer<R> serializer = configDefinition.getSerializer();

		Path location = serializer.getPath(configDefinition, valueContainer);

		try {
			Files.createDirectories(location.getParent());
			serializer.serialize(configDefinition, valueContainer);
		} catch (IOException e) {
			throw new ConfigSerializationException(String.format("Failed to serialize config '%s': %s", location, e.getMessage()));
		}
	}
}
