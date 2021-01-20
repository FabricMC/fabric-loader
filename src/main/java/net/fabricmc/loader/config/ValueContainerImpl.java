package net.fabricmc.loader.config;

import net.fabricmc.loader.api.config.*;
import net.fabricmc.loader.api.config.value.ConfigValue;
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
    private final Map<ConfigValue<?>, Object> values = new ConcurrentHashMap<>();
    private final Map<ConfigDefinition, Map<ConfigValue<?>, Boolean>> modifications = new ConcurrentHashMap<>();
    private final Collection<SaveType> saveTypes = new HashSet<>();

    public ValueContainerImpl(Path saveDirectory, SaveType... saveTypes) {
        this.saveDirectory = saveDirectory;
		this.saveTypes.addAll(Arrays.asList(saveTypes));
    }

    @Override
	@ApiStatus.Internal
    public <T> T put(@NotNull ConfigValue<T> configValue, @NotNull T newValue) {
    	SaveType saveType = configValue.getKey().getConfig().getSaveType();
    	if (!this.contains(saveType)) {
    		ConfigManager.LOGGER.warn("Error putting value '{}' for '{}'.", newValue, configValue);
    		ConfigManager.LOGGER.warn("ValueContainer does not support save type {}", saveType);
    		ConfigManager.LOGGER.warn("Valid save types are [{}]", this.saveTypes.stream().map(Object::toString).collect(Collectors.joining(", ")));
    		return null;
		}

        //noinspection unchecked
        T result = (T) (this.values.containsKey(configValue)
                        ? this.values.get(configValue)
                        : configValue.getDefaultValue());

        if (!newValue.equals(result)) {
            this.modifications.computeIfAbsent(configValue.getKey().getConfig(), key -> new HashMap<>()).put(configValue, true);
        }

        this.values.put(configValue, newValue);

        return result;
    }

    @Override
    public <T> T get(ConfigValue<T> configValue) {
        if (!this.values.containsKey(configValue)) {
            this.values.put(configValue, configValue.getDefaultValue());
        }

        //noinspection unchecked
        return (T) this.values.get(configValue);
    }

    @Override
    public int countUnsavedChanges(ConfigDefinition configDefinition) {
        return this.modifications.getOrDefault(configDefinition, Collections.emptyMap()).size();
    }

    @Override
    public boolean hasUnsavedChanges(ConfigDefinition configDefinition) {
        return this.countUnsavedChanges(configDefinition) > 0;
    }

    @Override
    public void save(ConfigDefinition configDefinition) {
        if (this.saveDirectory == null) {
            ConfigManager.LOGGER.warn("Attempted to save ValueContainer with null save directory.");
            return;
        }

        ConfigSerializer serializer = configDefinition.getSerializer();

        try {
            serializer.serialize(configDefinition, this);
        } catch (IOException e) {
			ConfigManager.LOGGER.error("Failed to save '{}' to disk", configDefinition);
        }

        this.modifications.remove(configDefinition);
    }

	@Override
	public boolean contains(SaveType saveType) {
		return this == ValueContainer.ROOT || this.saveTypes.contains(saveType);
	}

	@Override
    public Path getSaveDirectory() {
        return this.saveDirectory;
    }
}
