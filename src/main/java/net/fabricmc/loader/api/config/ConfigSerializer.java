package net.fabricmc.loader.api.config;

import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.config.value.ValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Implements serialization and deserialization behavior for config files.
 *
 *
 * <p>The config serializer is responsible for serializing a config definition and its values to a config file reading
 * those same types of files, and handling several other format-specific behavior. Serializers are responsible for
 * serializing any {@link Flag}, {@link Constraint}, or </p>
 */
public interface ConfigSerializer {
    void serialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException;
    void deserialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException;

	/**
	 * @param configDefinition an intermediate representation for a config file
	 * @param valueContainer the container holding values of {@param configDefinition}
	 * @return the version of an existing config file, null if one is not present
	 * @throws Exception if either loading the file or parsing the semantic version failed
	 */
    @Nullable SemanticVersion getVersion(ConfigDefinition configDefinition, ValueContainer valueContainer) throws Exception;

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
	}}
