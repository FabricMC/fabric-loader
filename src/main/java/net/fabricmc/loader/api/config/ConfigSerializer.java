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

import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.config.value.ValueKey;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Implements serialization and deserialization behavior for config files.
 *
 *
 * The config serializer is responsible for serializing a config definition and its values to a config file reading
 * those same types of files, and handling several other format-specific behavior. Serializers are responsible for
 * serializing any {@link Flag} or {@link Constraint} instances attached to the config definition, values, and versions.
 */
public interface ConfigSerializer {
	/**
	 * Saves all config values for a given definition to disk.
	 *
	 * Uses the list of config values defined in the config definition and their associated values stored in the value
	 * container to save a copy of the config file to disk. See {@link ConfigSerializer#getPath}.
	 *
	 * Note that if an IOException is thrown, the game will crash, as this is considered a critical failure.
	 *
	 * @param configDefinition an intermediate representation for a config file
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @throws IOException 	if saving the file failed
	 */
    default void serialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException {
		Path path = this.getPath(configDefinition, valueContainer);

		if (!Files.exists(path.getParent())) {
			Files.createDirectories(path.getParent());
		}

		this.serialize(configDefinition, Files.newOutputStream(path), valueContainer, v -> true, false);
	}

	/**
	 * Saves config values from a value container directly into an output stream.
	 *
	 * Exists primarily as a helper method for networking.
	 *
	 * @param configDefinition an intermediate representation for a config file
	 * @param outputStream the stream to write config values to
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @throws IOException 	if saving the file failed
	 */
	void serialize(ConfigDefinition configDefinition, OutputStream outputStream, ValueContainer valueContainer, Predicate<ValueKey<?>> valuePredicate, boolean minimal) throws IOException;

	/**
	 * Loads all config values for a given definition to disk.
	 *
	 * Uses the list of config values defined in the config definition to load the values from a file and store them in
	 * the provided value container. See {@link ConfigSerializer#getPath}.
	 *
	 * It is expected that config serializers will attempt to recover invalid config files as follows:
	 *  - If a file cannot be opened, return 'true' to create a backup
	 * 	- If a value cannot be parsed, ignore it, and return 'true' to create a backup
	 *
	 * Note that only one backup will be made for any given config version.
	 *
	 * If an IOException is thrown, the config file will be backed up and replaced with the default config file.
	 *
	 * @param configDefinition an intermediate representation for a config file
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @return whether or not this config file was successfully saved
	 * @throws IOException if loading the file failed
	 */
    default boolean deserialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException {
    	Path path = this.getPath(configDefinition, valueContainer);

    	return Files.exists(path) && this.deserialize(configDefinition, Files.newInputStream(path), valueContainer);
	}

	/**
	 * Loads config values into a value container directly from an input stream.
	 *
	 * Exists primarily as a helper method for networking.
	 *
	 * @param configDefinition an intermediate representation for a config file
	 * @param inputStream the input stream to read values from
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @return whether or not a backup should be made of the existing config file before saving the new one
	 * @throws IOException if loading the config failed
	 */
    boolean deserialize(ConfigDefinition configDefinition, InputStream inputStream, ValueContainer valueContainer) throws IOException;

	/**
	 * @return the file extension of this serializer, e.g. 'json', 'yaml', 'properties', etc.
	 */
	@NotNull String getExtension();

	/**
	 * Helper method for getting the path where a config file should be saved.
	 * @param configDefinition an intermediate representation for a config file
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @return the save location of the config file
	 */
    default @NotNull Path getPath(ConfigDefinition configDefinition, ValueContainer valueContainer) {
        return valueContainer.getSaveDirectory()
                .resolve(configDefinition.getPath()).normalize()
                .resolve(configDefinition.getName() + "." + this.getExtension());
    }

	/**
	 * Helper method for getting the path where a config file should be saved, with a suffix.
	 * @param configDefinition an intermediate representation for a config file
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @return the save location of the config file
	 */
	default @NotNull Path getPath(ConfigDefinition configDefinition, ValueContainer valueContainer, String suffix) {
		return valueContainer.getSaveDirectory()
				.resolve(configDefinition.getPath()).normalize()
				.resolve(configDefinition.getName() + "-" + suffix + "." + this.getExtension());
	}

}
