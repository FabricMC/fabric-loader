package net.fabricmc.loader.api.config;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;

/**
 * A top-level intermediate representation for several of the characteristics a config file needs.
 */
public class ConfigDefinition implements Comparable<ConfigDefinition>, Iterable<ConfigValue<?>> {
    private final String namespace;
    private final String name;
    private final ConfigSerializer serializer;
    private final Path path;
    private final String string;
    private final SaveType saveType;
    private final SemanticVersion version;
    private final Multimap<DataType<?>, Object> data = LinkedHashMultimap.create();

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param saveType see {@link SaveType}
	 * @param version the version of this config definition, used for backing up and migrating from old configs
	 * @param path the path of the directory this config file, relative to 'config/namespace'
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull ConfigSerializer serializer, @NotNull SaveType saveType, SemanticVersion version, Multimap<DataType<?>, Object> data, @NotNull Path path) {
        this.namespace = namespace;
        this.name = name;
        this.serializer = serializer;
		this.version = version;
		this.data.putAll(data);
		this.path = path;
        this.saveType = saveType;
        this.string = namespace + ":" + name;

        if (!isValid(namespace)) {
            throw new ConfigIdentifierException("Non [a-z0-9_.-] character in namespace of config key: " + this.string);
        } else if (!isValid(name)) {
            throw new ConfigIdentifierException("Non [a-z0-9_.-] character in name of config key: " + this.string);
        }
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param saveType see {@link SaveType}
	 * @param version the version of this config definition, used for backing up and migrating from old configs
	 * @param path the path of the directory this config file, relative to 'config/namespace'
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull ConfigSerializer serializer, @NotNull SaveType saveType, SemanticVersion version, Multimap<DataType<?>, Object> data, String... path) {
        this(namespace, name, serializer, saveType, version, data, Paths.get(namespace, path));
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param saveType see {@link SaveType}
	 * @param version the version of this config definition, used for backing up and migrating from old configs
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull ConfigSerializer serializer, @NotNull SaveType saveType, SemanticVersion version, Multimap<DataType<?>, Object> data) {
        this(namespace, name, serializer, saveType, version, data, Paths.get("."));
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param saveType see {@link SaveType}
	 * @param version the version of this config definition, used for backing up and migrating from old configs
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull ConfigSerializer serializer, @NotNull SaveType saveType, SemanticVersion version, Multimap<DataType<?>, Object> data) {
        this(namespace, "config", serializer, saveType, version, data, Paths.get("."));
    }

	@SuppressWarnings("unchecked")
	public <D> Iterable<D> getData(DataType<D> dataType) {
		return (Iterable<D>) this.data.get(dataType);
	}

    public @NotNull String getNamespace() {
        return this.namespace;
    }

    public @NotNull String getName() {
        return this.name;
    }

    public @NotNull ConfigSerializer getSerializer() {
        return this.serializer;
    }

    public @NotNull Path getPath() {
        return this.path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigDefinition configDefinition = (ConfigDefinition) o;
        return namespace.equals(configDefinition.namespace) && name.equals(configDefinition.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public String toString() {
        return this.string;
    }

    public static boolean isValid(String string) {
        for(int i = 0; i < string.length(); ++i) {
            if (!isCharacterValid(string.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCharacterValid(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    @Override
    public int compareTo(@NotNull ConfigDefinition o) {
        int c = this.namespace.compareTo(o.namespace);

        if (c == 0) {
            return this.name.compareTo(o.name);
        } else {
            return c;
        }
    }

    public SaveType getSaveType() {
        return this.saveType;
    }

	public SemanticVersion getVersion() {
		return this.version;
	}

	@NotNull
	@Override
	public Iterator<ConfigValue<?>> iterator() {
		return ConfigManager.getValues(this).iterator();
	}
}
