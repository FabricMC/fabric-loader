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

import net.fabricmc.loader.api.config.*;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ValueContainerImpl implements ValueContainer {
    private final Path saveDirectory;
    private final Map<ValueKey<?>, Object> values = new ConcurrentHashMap<>();
    private final Map<ConfigDefinition, Map<ValueKey<?>, Boolean>> modifications = new ConcurrentHashMap<>();
    private final Collection<SaveType> saveTypes = new HashSet<>();

    public ValueContainerImpl(Path saveDirectory, SaveType... saveTypes) {
        this.saveDirectory = saveDirectory;
		this.saveTypes.addAll(Arrays.asList(saveTypes));
    }

    @Override
	@ApiStatus.Internal
    public <T> T put(@NotNull ValueKey<T> valueKey, @NotNull T newValue) {
    	SaveType saveType = valueKey.getConfig().getSaveType();
    	if (!this.contains(saveType)) {
    		ConfigManagerImpl.LOGGER.warn("Error putting value '{}' for '{}'.", newValue, valueKey);
    		ConfigManagerImpl.LOGGER.warn("ValueContainer does not support save type {}", saveType);
    		ConfigManagerImpl.LOGGER.warn("Valid save types are [{}]", this.saveTypes.stream().map(Object::toString).collect(Collectors.joining(", ")));
    		return null;
		}

        //noinspection unchecked
        T result = (T) (this.values.containsKey(valueKey)
                        ? this.values.get(valueKey)
                        : valueKey.getDefaultValue());

        if (!newValue.equals(result)) {
            this.modifications.computeIfAbsent(valueKey.getConfig(), key -> new HashMap<>()).put(valueKey, true);
        }

        this.values.put(valueKey, newValue);

        return result;
    }

    @Override
    public <T> T get(ValueKey<T> valueKey) {
        if (!this.values.containsKey(valueKey)) {
            this.values.put(valueKey, valueKey.getDefaultValue());
        }

        //noinspection unchecked
        return (T) this.values.get(valueKey);
    }

    @Override
    public int countUnsavedChanges(ConfigDefinition configDefinition) {
        return this.modifications.getOrDefault(configDefinition, Collections.emptyMap()).size();
    }

    @Override
    public void save(ConfigDefinition configDefinition) {
        if (this.saveDirectory == null) {
            ConfigManagerImpl.LOGGER.warn("Attempted to save ValueContainer with null save directory.");
            return;
        }

        ConfigSerializer serializer = configDefinition.getSerializer();

        try {
            serializer.serialize(configDefinition, this);
        } catch (IOException e) {
			ConfigManagerImpl.LOGGER.error("Failed to save '{}' to disk", configDefinition);
        }

        this.modifications.remove(configDefinition);
    }

	@Override
	public boolean contains(SaveType saveType) {
		return this.saveTypes.contains(saveType);
	}

	@Override
    public Path getSaveDirectory() {
        return this.saveDirectory;
    }

    public void add(SaveType... saveTypes) {
    	this.saveTypes.addAll(Arrays.asList(saveTypes));
	}
}
