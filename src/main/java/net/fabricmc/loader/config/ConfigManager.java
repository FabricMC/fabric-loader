package net.fabricmc.loader.config;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.api.config.*;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigSerializationException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.entrypoint.ConfigInitializer;
import net.fabricmc.loader.api.entrypoint.ConfigProvider;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.entrypoint.minecraft.hooks.EntrypointUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class ConfigManager {
    public static final Logger LOGGER = LogManager.getLogger("Fabric|Config");
    private static final Multimap<ConfigDefinition, ConfigValue<?>> CONFIGS = LinkedHashMultimap.create();

    private static boolean FINISHED = false;

    public static boolean isFinished() {
        return FINISHED;
    }

    public static Collection<ConfigDefinition> getConfigKeys() {
    	return CONFIGS.keys();
	}

    public static Collection<ConfigValue<?>> getValues(ConfigDefinition configDefinition) {
        return CONFIGS.get(configDefinition);
    }

    public static void initialize() {
    	if (isFinished()) return;

    	Multimap<String, ConfigInitializer> configInitializers = LinkedHashMultimap.create();

        for (EntrypointContainer<ConfigInitializer> container : FabricLoader.getInstance().getEntrypointContainers("config", ConfigInitializer.class)) {
            String modId = container.getProvider().getMetadata().getId();
            ConfigInitializer initializer = container.getEntrypoint();

            configInitializers.put(modId, initializer);
        }

        for (EntrypointContainer<ConfigProvider> container : FabricLoader.getInstance().getEntrypointContainers("configProvider", ConfigProvider.class)) {
			container.getEntrypoint().addConfigs(configInitializers::put);
		}

        for (String modId : configInitializers.keys()) {
			for (ConfigInitializer initializer : configInitializers.get(modId)) {
				Multimap<DataType<?>, Object> data = LinkedHashMultimap.create();

				initializer.addConfigData(data::put);

				ConfigDefinition configDefinition = new ConfigDefinition(modId, initializer.getName(), initializer.getSerializer(), initializer.getSaveType(), initializer.getVersion(), data, initializer.getSavePath());

				if (CONFIGS.containsKey(configDefinition)) {
					LOGGER.warn("Attempted to register duplicate config '{}'", configDefinition.toString());
					continue;
				}

				initializer.addConfigValues(((configValue, path0, path) -> {
					ValueKey key = new ValueKey(configDefinition, path0, path);
					configValue.setKey(key);

					if (CONFIGS.get(configDefinition).contains(configValue)) {
						LOGGER.warn("Attempted to register duplicate config value '{}'", configValue);
						return;
					}

					CONFIGS.put(configDefinition, configValue);
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
				LOGGER.warn("Expected config version '{}'. Found '{}'.", configDefinition.getVersion(), version);
				backup(valueContainer, configDefinition, serializer);
			}
		} catch (Exception ignored) {
		}

		try {
			if (Files.exists(serializer.getPath(configDefinition, valueContainer))) {
				serializer.deserialize(configDefinition, valueContainer);
			}
        } catch (IOException e) {
			LOGGER.warn("Failed to deserialize config '{}'", e.getMessage());
			backup(valueContainer, configDefinition, serializer);
		} catch (Exception e) {
			throw new ConfigSerializationException(e);
		}

		try {
            serializer.serialize(configDefinition, valueContainer);
        } catch (IOException e) {
            LOGGER.error("Failed to serialize config '{}'", e.getMessage());
        } catch (Exception e) {
			throw new ConfigSerializationException(e);
		}
	}

    private static void backup(ValueContainer valueContainer, ConfigDefinition configDefinition, ConfigSerializer serializer) {
		Path location = serializer.getPath(configDefinition, valueContainer);
		Path backupLocation = serializer.getPath(configDefinition, valueContainer, configDefinition.getVersion().getFriendlyString());

		LOGGER.warn("Backing up config file at '{}' to '{}'", location.getFileName(), backupLocation.getFileName());

		try {
			Files.copy(location, backupLocation);
		} catch (IOException e) {
			throw new ConfigSerializationException(e);
		}
	}
}
