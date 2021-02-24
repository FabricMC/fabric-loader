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

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.SaveType;
import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import net.fabricmc.loader.api.config.serialization.ConfigSerializer;
import net.fabricmc.loader.api.config.util.ConfigUpgrade;
import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.config.ConfigManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A top-level intermediate representation for several of the characteristics a config file needs.
 */
public class ConfigDefinition<R> implements Comparable<ConfigDefinition<?>>, Iterable<ValueKey<?>> {
    private final String namespace;
    private final String name;
    private final SemanticVersion version;
    private final ConfigSerializer<R> serializer;
    private final Path path;
    private final String string;
    private final SaveType saveType;
	private final Map<DataType<?>, List<Object>> data = new HashMap<>();
	private final ConfigUpgrade<R> upgrade;

	/**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param version version
	 * @param saveType see {@link SaveType}
	 * @param upgrade the upgrader to be used
	 * @param path the path of the directory this config file, relative to 'config/namespace'
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull SemanticVersion version, @NotNull ConfigSerializer<R> serializer, @NotNull SaveType saveType, @NotNull ConfigUpgrade<R> upgrade, @NotNull Path path, Map<DataType<?>, Collection<Object>> data) {
        this.namespace = namespace;
        this.name = name;
		this.version = version;
		this.serializer = serializer;
		this.upgrade = upgrade;
		this.path = path;
        this.saveType = saveType;
        this.string = namespace + ":" + name;

		data.forEach((type, collection) -> this.data.put(type, new ArrayList<>(collection)));

        if (!isValid(namespace)) {
            throw new ConfigIdentifierException("Non [a-z0-9_.-] character in namespace of config key: " + this.string);
        } else if (!isValid(name)) {
            throw new ConfigIdentifierException("Non [a-z0-9_.-] character in name of config key: " + this.string);
        }
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param version
	 * @param saveType see {@link SaveType}
	 * @param upgrade
	 * @param path the path of the directory this config file, relative to 'config/namespace'
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull SemanticVersion version, @NotNull SaveType saveType, Map<DataType<?>, Collection<Object>> data, @NotNull ConfigSerializer<R> serializer, @NotNull ConfigUpgrade<R> upgrade, String... path) {
        this(namespace, name, version, serializer, saveType, upgrade, Paths.get(namespace, path), data);
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param name the name of the config file this key represents (without any file extensions)
	 * @param saveType see {@link SaveType}
	 * @param version
	 * @param upgrade
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull String name, @NotNull ConfigSerializer<R> serializer, @NotNull SaveType saveType, Map<DataType<?>, Collection<Object>> data, SemanticVersion version, ConfigUpgrade<R> upgrade) {
        this(namespace, name, version, serializer, saveType, upgrade, Paths.get("."), data);
    }

    /**
	 * @param namespace namespace of the entity that owns this config file, usually a mod id
	 * @param saveType see {@link SaveType}
	 * @param version
	 * @param upgrade
	 */
    public ConfigDefinition(@NotNull String namespace, @NotNull ConfigSerializer<R> serializer, @NotNull SaveType saveType, Map<DataType<?>, Collection<Object>> data, SemanticVersion version, ConfigUpgrade<R> upgrade) {
        this(namespace, "config", version, serializer, saveType, upgrade, Paths.get("."), data);
    }

	@SuppressWarnings("unchecked")
	public <D> ListView<D> getData(DataType<D> dataType) {
		return new ListView<>((List<D>) this.data.getOrDefault(dataType, Collections.emptyList()));
	}

    public @NotNull String getNamespace() {
        return this.namespace;
    }

    public @NotNull String getName() {
        return this.name;
    }

    public @NotNull ConfigSerializer<R> getSerializer() {
        return this.serializer;
    }

    public @NotNull Path getPath() {
        return this.path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigDefinition<?> configDefinition = (ConfigDefinition<?>) o;
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

    public boolean upgrade(@Nullable SemanticVersion from, R representation) {
    	return this.upgrade.upgrade(from, representation);
	}

	@SafeVarargs
	public final <D> void add(DataType<D> dataType, D... data) {
		this.add(dataType, Arrays.asList(data));
	}

	public <D> void add(DataType<D> dataType, Collection<D> data) {
		assertNotPostInitialized();

		this.data.computeIfAbsent(dataType, t -> new ArrayList<>()).addAll(data);
	}

	@NotNull
	@Override
	public Iterator<ValueKey<?>> iterator() {
		return ConfigManager.getValues(this).iterator();
	}

	public @NotNull SemanticVersion getVersion() {
		return this.version;
	}

	private static void assertNotPostInitialized() {
		if (ConfigManagerImpl.isFinished()) {
			throw new ConfigValueException("Post initializers already finished!");
		}
	}
}
