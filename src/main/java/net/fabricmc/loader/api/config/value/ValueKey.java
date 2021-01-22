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

package net.fabricmc.loader.api.config.value;

import com.google.common.collect.*;
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import net.fabricmc.loader.config.ValueContainerProviders;
import net.minecraft.entity.EquipmentSlot;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Represents a value that can be loaded from/saved to a config file.
 * @param <T> the type of value to be stored
 */
public class ValueKey<T> implements Comparable<ValueKey<?>> {
    private final Supplier<T> defaultValue;
    private final ImmutableCollection<Constraint<T>> constraints;
	private final ImmutableCollection<Flag> flags;
	private final Map<DataType<?>, Collection<Object>> data = new HashMap<>();
	private final ImmutableCollection<BiConsumer<T, T>> listeners;
	private final ImmutableCollection<TriConsumer<T, T, UUID>> playerListeners;

	private boolean initialized;

	private ConfigDefinition config;
	private String[] path;
	private String pathString;
	private String string;

	private ValueKey(@NotNull Supplier<@NotNull T> defaultValue, Collection<Constraint<T>> constraints, Collection<Flag> flags, Map<DataType<?>, Collection<Object>> data, List<BiConsumer<T, T>> listeners, List<TriConsumer<T, T, UUID>> playerListeners) {
        this.defaultValue = defaultValue;
		this.constraints = ImmutableList.copyOf(constraints);
		this.flags = ImmutableSet.copyOf(flags);

		data.forEach((type, collection) -> this.data.computeIfAbsent(type, t -> new ArrayList<>()).addAll(collection));

		this.listeners = ImmutableList.copyOf(listeners);
		this.playerListeners = ImmutableList.copyOf(playerListeners);
	}

    /**
     * Gets the default value for this config key for initial population of config files and reset purposes.
     * @return the default value of this config value
     */
    public T getDefaultValue() {
        return this.defaultValue.get();
    }

    @Override
    public int compareTo(@NotNull ValueKey<?> o) {
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

	/**
	 * Gets the value represented by this config key.
	 *
	 * Will get the config value from the appropriate value container, or the root value container if none match.
	 *
	 * @return a value
	 */
    public T getValue() {
		this.assertInitialized();

        ValueContainerProvider provider = ValueContainerProviders.getInstance(this.config.getSaveType());

        return provider.getValueContainer().get(this);
    }

	/**
	 * Gets the value represented by this config key for the specified player.
	 *
	 * Only functions if the game in question implements syncing, otherwise the default value is returned.
	 *
	 * @param playerId the id of the player to query
	 * @return a value
	 */
	public T getValue(UUID playerId) {
		this.assertInitialized();

		ValueContainerProvider provider = ValueContainerProviders.getInstance(this.config.getSaveType());

		return provider.getPlayerValueContainer(playerId).get(this);
	}

	/**
	 * Gets the value represented by this config value.
	 *
	 * Will set the config value in the appropriate value container, or the root value container if none match.
	 *
	 * @param newValue the value to set
	 * @return the previous value of this config value
	 */
	public T setValue(T newValue) {
		this.assertInitialized();
		this.assetConstraints(newValue);

		ValueContainer valueContainer = ValueContainerProviders.getInstance(this.config.getSaveType()).getValueContainer();

		T oldValue = valueContainer.put(this, newValue);

		this.listeners.forEach(listener -> listener.accept(oldValue, newValue));

		return oldValue;
	}

	/**
	 * Sets the value represented by this config key for the specified player.
	 *
	 * Only functions if the game in question implements syncing, otherwise the default value is returned.
	 *
	 * @param playerId the id of the player to query
	 * @param newValue the value to set
	 * @return the previous value of this config value
	 */
	public T setValue(UUID playerId, T newValue) {
		this.assertInitialized();
		this.assetConstraints(newValue);

		ValueContainer valueContainer = ValueContainerProviders.getInstance(this.config.getSaveType()).getPlayerValueContainer(playerId);

		T oldValue = valueContainer.put(this, newValue);

		this.playerListeners.forEach(listener -> listener.accept(oldValue, newValue, playerId));

		return oldValue;
	}

	/**
	 * Sets the value represented by this config key in the specified value container.
	 *
	 * @param newValue the value to set
	 * @param valueContainer the container to update
	 * @return the previous value of this config value
	 */
	public T setValue(T newValue, ValueContainer valueContainer) {
		this.assertInitialized();
		this.assetConstraints(newValue);

		T oldValue = valueContainer.put(this, newValue);

		this.listeners.forEach(listener -> listener.accept(oldValue, newValue));

		return oldValue;
	}

	/**
	 * @param value the value to check against this keys constraints
	 * @return true if the value passes all constraints, false otherwise
	 */
	public boolean isWithinConstraints(T value) {
		this.assertInitialized();

		for (Constraint<T> constraint : this.constraints) {
			if (!constraint.passes(value)) return false;
		}

    	return true;
	}

	/**
	 * @param flag the flag to check
	 * @return true if the flag is present, false otherwise
	 */
	public boolean isFlagSet(Flag flag) {
    	return this.flags.contains(flag);
	}

    @NotNull
    public Iterable<Constraint<T>> getConstraints() {
        return this.constraints;
    }

	@NotNull
	public Iterable<Flag> getFlags() {
		return this.flags;
	}

	@SuppressWarnings("unchecked")
	public <D> Iterable<D> getData(DataType<D> dataType) {
		return (Iterable<D>) this.data.getOrDefault(dataType, Collections.emptyList());
	}

	/**
	 * @return the parent config file that this key belongs to
	 */
	public ConfigDefinition getConfig() {
		this.assertInitialized();

		return this.config;
	}

	/**
	 * @return the fully qualified path for this value in the form 'modid:config_name/path/values'
	 */
	@Override
	public String toString() {
		this.assertInitialized();

		return this.string;
	}

	/**
	 * @return the path for this value in the form 'path/values'
	 */
	public String getPathString() {
		this.assertInitialized();

		return this.pathString;
	}

	public String[] getPath() {
		return this.path;
	}

	private boolean isInitialized() {
		return this.initialized;
	}

	private void assertInitialized() {
		if (!this.isInitialized()) {
			throw new ConfigValueException("ValueKey not properly initialized!");
		}
	}

	private void assetConstraints(T value) {
		if (!isWithinConstraints(value)) {
			throw new ConfigValueException("Value '" + value + "' is not within constraints for key '" + this.string + "'");
		}
	}

	/**
	 * Sets the key that can be used to refer to this config value.
	 * Must be initialized lazily so that different config providers can derive the keys in different ways.
	 * @param configDefinition the config file this value belongs to
	 * @param path0 the first element in the path of this value key
	 * @param path additional elements in the path of this value key, for nested values
	 */
	@ApiStatus.Internal
	public void initialize(ConfigDefinition configDefinition, @NotNull String path0, String[] path) {
		this.config = configDefinition;
		this.path = new String[path.length + 1];
		this.path[0] = path0;
		System.arraycopy(path, 0, this.path, 1, path.length);
		this.pathString = String.join("/", this.path);
		this.string = configDefinition.toString() + "/" + this.pathString;

		for (String string : this.path) {
			if (!ConfigDefinition.isValid(string)) {
				throw new ConfigIdentifierException("Non [a-z0-9_.-] character in name of value key: " + this.string);
			}
		}

		this.initialized = true;
	}

	public static class Builder<T> {
		private final Supplier<T> defaultValue;
		private final Collection<Constraint<T>> constraints = new ArrayList<>();
		private final List<Flag> flags = new ArrayList<>();
		private final Map<DataType<?>, Collection<Object>> data = new HashMap<>();
		private final List<BiConsumer<T, T>> listeners = new ArrayList<>();
		private final List<TriConsumer<T, T, UUID>> playerListeners = new ArrayList<>();

		/**
		 * @param defaultValue the value to be used when a config file doesn't exist or the value needs to be reset
		 */
		public Builder(@NotNull Supplier<@NotNull T> defaultValue) {
			this.defaultValue = defaultValue;
		}

		/**
		 * @param constraints any number of constraints to be applied to this config value
		 * @return this
		 */
		@SafeVarargs
		public final Builder<T> with(Constraint<T>... constraints) {
			this.constraints.addAll(Arrays.asList(constraints));
			return this;
		}

		/**
		 * @param flags see {@link Flag}
		 * @return this
		 */
		public final Builder<T> with(Flag... flags) {
			this.flags.addAll(Arrays.asList(flags));
			return this;
		}

		/**
		 * @param type the type of data to add to this value
		 * @param data the value of the data to be added
		 * @return this
		 */
		public final <D> Builder<T> with(DataType<D> type, D data) {
			this.data.computeIfAbsent(type, t -> new ArrayList<>()).add(data);
			return this;
		}

		/**
		 * Adds the provided listeners to the config value built by this builder.
		 *
		 * <p>Listeners will be fired when a config value is successfully set post-initialization. They will not be
		 * fired if the new value fails to satisfy its constraints.</p>
		 *
		 * <p>The first parameter of the listener is the old value, while the second parameter is the new value. The
		 * old value may be null if it was not previously present in the value container its been added to.</p>
		 *
		 * @param listeners any number of listeners
		 * @return this
		 */
		@SafeVarargs
		public final Builder<T> with(BiConsumer<@Nullable T, T>... listeners) {
			this.listeners.addAll(Arrays.asList(listeners));
			return this;
		}

		/**
		 * Adds the provided player value listeners to the config value built by this builder.
		 *
		 * <p>Listeners will be fired when a config value is successfully set post-initialization. They will not be
		 * fired if the new value fails to satisfy its constraints.</p>
		 *
		 * <p>The first parameter of the listener is the old value, while the second parameter is the new value,
		 * and the UUID is the id of the player whom this value was set for. The old value may be null if it was not
		 * previously present in the value container its been added to.</p>
		 *
		 * @param listeners any number of listeners
		 * @return this
		 */
		@SafeVarargs
		public final Builder<T> with(TriConsumer<@Nullable T, T, UUID>... listeners) {
			this.playerListeners.addAll(Arrays.asList(listeners));
			return this;
		}

		public final ValueKey<T> build() {
			return new ValueKey<>(this.defaultValue, this.constraints, this.flags, this.data, this.listeners, this.playerListeners);
		}
	}
}
