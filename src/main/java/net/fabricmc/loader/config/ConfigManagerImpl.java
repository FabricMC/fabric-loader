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
import net.fabricmc.loader.api.config.serialization.ConfigSerializer;
import net.fabricmc.loader.api.config.entrypoint.ConfigEnvironment;
import net.fabricmc.loader.api.config.entrypoint.ConfigPostInitializer;
import net.fabricmc.loader.api.config.data.DataCollector;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigSerializationException;
import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.config.entrypoint.ConfigInitializer;
import net.fabricmc.loader.api.config.entrypoint.ConfigProvider;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManagerImpl {
	public static final Logger LOGGER = LogManager.getLogger("Fabric|Config");
	private static final Map<ConfigDefinition<?>, List<ValueKey<?>>> CONFIGS = new HashMap<>();
	private static final Map<ConfigDefinition<?>, ListView<ValueKey<?>>> CONFIG_VIEWS = new HashMap<>();
    private static final Map<String, ConfigDefinition<?>> CONFIG_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, ValueKey<?>> CONFIG_VALUES = new ConcurrentHashMap<>();

	private static ListView<ConfigDefinition<?>> CONFIG_DEFINITION_VIEW = null;

    private static boolean FINISHED = false;

    public static boolean isFinished() {
        return FINISHED;
    }

    public static ListView<ConfigDefinition<?>> getConfigKeys() {
    	return CONFIG_DEFINITION_VIEW;
	}

    public static ListView<ValueKey<?>> getValues(ConfigDefinition<?> configDefinition) {
        return CONFIG_VIEWS.computeIfAbsent(configDefinition, definition ->
				new ListView<>(CONFIGS.getOrDefault(definition, Collections.emptyList())));
    }

    public static @Nullable ValueKey<?> getValue(String configKeyString) {
    	return CONFIG_VALUES.get(configKeyString);
	}

	public static <R> @Nullable ConfigDefinition<R> getDefinition(String configKeyString) {
		//noinspection unchecked
		return (ConfigDefinition<R>) CONFIG_DEFINITIONS.get(configKeyString);
	}

    public static void initialize() {
    	if (FINISHED) return;

    	Map<String, Collection<ConfigInitializer<?>>> configInitializers = new HashMap<>();
    	Collection<ConfigPostInitializer> postInitializers = new ArrayList<>();

    	// We use one entrypoint to reduce the number of excess entrypoint keys
    	for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("config", Object.class)) {
			Object entrypoint = container.getEntrypoint();

			// Each entrypoint type is handled individually
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
			for (ConfigInitializer<?> initializer : configInitializers.get(modId)) {
				initialize(modId, initializer);
			}
		}

        CONFIG_DEFINITION_VIEW = new ListView<>(CONFIG_DEFINITIONS.values());

		postInitializers.forEach(ConfigPostInitializer::onConfigsLoaded);

        for (ConfigDefinition<?> configDefinition : CONFIG_DEFINITIONS.values()) {
        	doSerialization(configDefinition, ValueContainer.ROOT);
		}

        FINISHED = true;
    }

    private static <T1> void initialize(String modId, ConfigInitializer<T1> initializer) {
		Map<DataType<?>, Collection<Object>> data = new HashMap<>();

		initializer.addConfigData(new DataCollector() {
			@Override
			public <T2> void add(DataType<T2> type, Collection<T2> values) {
				data.computeIfAbsent(type, t -> new ArrayList<>()).addAll(values);
			}
		});

		ConfigDefinition<T1> configDefinition = new ConfigDefinition<>(modId, initializer.getName(), initializer.getVersion(), initializer.getSaveType(), data, initializer.getSerializer(), initializer, initializer.getSavePath());

		if (CONFIGS.containsKey(configDefinition)) {
			LOGGER.warn("Attempted to register duplicate config '{}'", configDefinition.toString());
			return;
		}

		initializer.addConfigValues(((configValue, path0, path) -> {
			configValue.initialize(configDefinition, path0, path);

			if (CONFIGS.getOrDefault(configDefinition, Collections.emptyList()).contains(configValue)) {
				LOGGER.warn("Attempted to register duplicate config value '{}'", configValue);
				return;
			}

			CONFIGS.computeIfAbsent(configDefinition, c -> new ArrayList<>()).add(configValue);
			CONFIG_VALUES.put(configValue.toString(), configValue);
			CONFIG_DEFINITIONS.put(configDefinition.toString(), configDefinition);
		}));
	}

    public static <R> void doSerialization(ConfigDefinition<R> configDefinition, ValueContainer valueContainer) {
    	if (!valueContainer.contains(configDefinition.getSaveType())) return;

        ConfigSerializer<R> serializer = configDefinition.getSerializer();

		try {
			serializer.deserialize(configDefinition, valueContainer);
        } catch (IOException e) {
			Path location = serializer.getPath(configDefinition, valueContainer);
			throw new ConfigSerializationException(String.format("Failed to deserialize config '%s': %s", location, e.getMessage()));
		}

		save(configDefinition, valueContainer);
	}

	public static <R> void save(ConfigDefinition<R> configDefinition, ValueContainer valueContainer) {
    	if (configDefinition != null && valueContainer != null) {
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
}
