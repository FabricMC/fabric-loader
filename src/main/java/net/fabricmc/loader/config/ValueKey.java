package net.fabricmc.loader.config;

import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ValueKey implements Comparable<ValueKey> {
    private final ConfigDefinition config;
    private final String[] path;
    private final String string;
	private final String pathString;

	/**
     * @param configDefinition the config file this value belongs to
     * @param path0 the first element in the path of this value key
     * @param path additional elements in the path of this value key, for nested values
     */
    public ValueKey(ConfigDefinition configDefinition, String path0, String... path) {
        this.config = configDefinition;
        this.path = new String[path.length + 1];
        this.path[0] = path0;
        System.arraycopy(path, 0, this.path, 1, path.length);
        this.pathString = String.join("/", this.path);
        this.string = configDefinition.toString() + "/" + this.pathString;

        for (String string : this.path) {
            if (!ConfigDefinition.isValid(string)) {
                throw new ConfigValueException("Non [a-z0-9_.-] character in name of value key: " + this.string);
            }
        }
    }

    public ConfigDefinition getConfig() {
        return config;
    }

    public String[] getPath() {
    	return Arrays.copyOf(this.path, this.path.length);
	}

    @Override
    public String toString() {
        return this.string;
    }

    public String getPathString() {
    	return this.pathString;
	}

    @Override
    public int compareTo(@NotNull ValueKey o) {
        if (!this.config.equals(o.config)) {
            return this.config.compareTo(o.config);
        } else if (this.path.length != o.path.length) {
            return Integer.compare(this.path.length, o.path.length);
        } else {
            for (int i = 0; i < this.path.length; ++i) {
                if (!this.path[i].equals(o.path[i])) {
                    return this.path[i].compareTo(o.path[i]);
                }
            }
        }

        return 0;
    }
}
