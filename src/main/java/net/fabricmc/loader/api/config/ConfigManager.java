package net.fabricmc.loader.api.config;

import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.config.ConfigManagerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ConfigManager {
	Logger LOGGER = LogManager.getLogger("Fabric|Config");

	static Collection<ConfigDefinition> getConfigKeys() {
		return ConfigManagerImpl.getConfigKeys();
	}

	static Collection<ConfigValue<?>> getValues(ConfigDefinition configDefinition) {
		return ConfigManagerImpl.getValues(configDefinition);
	}

	static @Nullable ConfigValue<?> getValue(String configKeyString) {
		return ConfigManagerImpl.getValue(configKeyString);
	}

	static void doSerialization(ConfigDefinition config, ValueContainer valueContainer) {
		ConfigManagerImpl.doSerialization(config, valueContainer);
	}

	static @Nullable ConfigDefinition getDefinition(String configKeyString) {
		return ConfigManagerImpl.getDefinition(configKeyString);
	}
}
