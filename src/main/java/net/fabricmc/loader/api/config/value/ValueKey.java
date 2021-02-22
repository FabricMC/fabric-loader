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

import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.KeyView;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.util.StronglyTypedImmutableCollection;
import net.fabricmc.loader.api.config.util.Table;
import net.fabricmc.loader.api.config.util.TriConsumer;
import net.fabricmc.loader.config.ConfigManagerImpl;
import net.fabricmc.loader.config.ValueContainerProviders;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents a value that can be loaded from/saved to a config file.
 * @param <T> the type of value to be stored
 */
public final class ValueKey<T> implements Comparable<ValueKey<?>>, KeyView<T> {
    private final Supplier<T> defaultValue;
    private final List<Constraint<T>> constraints;
	private final List<Flag> flags;
	private final Map<DataType<?>, List<Object>> data = new LinkedHashMap<>();
	private final List<BiConsumer<T, T>> listeners;
	private final List<TriConsumer<T, T, UUID>> playerListeners;

	private boolean initialized;

	private ConfigDefinition<?> config;
	private String[] path;
	private String pathString;
	private String string;

	ValueKey(@NotNull Supplier<@NotNull T> defaultValue, Collection<Constraint<T>> constraints, Collection<Flag> flags, Map<DataType<?>, Collection<Object>> data, List<BiConsumer<T, T>> listeners, List<TriConsumer<T, T, UUID>> playerListeners) {
        this.defaultValue = defaultValue;
		this.constraints = new ArrayList<>(constraints);
		this.flags = new ArrayList<>(flags);

		data.forEach((type, collection) -> this.data.put(type, new ArrayList<>(collection)));

		this.listeners = new ArrayList<>(listeners);
		this.playerListeners = new ArrayList<>(playerListeners);

		T value = defaultValue.get();
		for (Constraint<T> constraint : this.constraints) {
			if (!constraint.passes(value)) throw new ConfigValueException("Default value is not within constraints");
		}
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

        return this.getValue(provider.getValueContainer());
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

		return this.getValue(provider.getPlayerValueContainer(playerId));
	}

	/**
	 * Gets the value represented by this config key.
	 *
	 * Will get the config value from the appropriate value container, or the root value container if none match.
	 *
	 * @return a value
	 */
	public T getValue(ValueContainer valueContainer) {
		this.assertInitialized();

		return valueContainer.get(this);
	}

	/**
	 * Sets the value represented by this config value.
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
    public ListView<Constraint<T>> getConstraints() {
        return new ListView<>(this.constraints);
    }

    public void add(Flag... flags) {
		this.add(Arrays.asList(flags));
	}

	public void add(Collection<Flag> flags) {
		assertNotPostInitialized();

		this.flags.addAll(flags);
	}

	@NotNull
	public ListView<Flag> getFlags() {
		return new ListView<>(this.flags);
	}

	@SafeVarargs
	public final <D> void add(DataType<D> dataType, D... data) {
		this.add(dataType, Arrays.asList(data));
	}

	public <D> void add(DataType<D> dataType, Collection<D> data) {
		assertNotPostInitialized();

		this.data.computeIfAbsent(dataType, t -> new ArrayList<>()).addAll(data);
	}

	@SuppressWarnings("unchecked")
	public <D> ListView<D> getData(DataType<D> dataType) {
		return new ListView<>((List<D>) this.data.getOrDefault(dataType, Collections.emptyList()));
	}

	@Override
	public ListView<DataType<?>> getDataTypes() {
		return new ListView<>(new ArrayList<>(this.data.keySet()));
	}

	/**
	 * @return the parent config file that this key belongs to
	 */
	public ConfigDefinition<?> getConfig() {
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

	public boolean isInitialized() {
		return this.initialized;
	}

	private void assertInitialized() {
		if (!this.isInitialized()) {
			throw new ConfigValueException("ValueKey not properly initialized!");
		}
	}

	private static void assertNotPostInitialized() {
		if (ConfigManagerImpl.isFinished()) {
			throw new ConfigValueException("Post initializers already finished!");
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
	public void initialize(ConfigDefinition<?> configDefinition, @NotNull String path0, String[] path) {
		if (this.initialized) {
			throw new ConfigValueException("Config value '" + this.string + "' already initialized");
		}

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

	public abstract static class AbstractBuilder<T> {
		protected final Supplier<T> defaultValue;
		protected final List<Flag> flags = new ArrayList<>();
		protected final Map<DataType<?>, Collection<Object>> data = new HashMap<>();
		protected final List<BiConsumer<T, T>> listeners = new ArrayList<>();
		protected final List<TriConsumer<T, T, UUID>> playerListeners = new ArrayList<>();

		protected AbstractBuilder(Supplier<T> defaultValue) {
			this.defaultValue = defaultValue;
		}

		/**
		 * @param flags see {@link Flag}
		 * @return this
		 */
		public final AbstractBuilder<T> with(Flag... flags) {
			this.flags.addAll(Arrays.asList(flags));
			return this;
		}

		/**
		 * @param flags see {@link Flag}
		 * @return this
		 */
		public final AbstractBuilder<T> with(Collection<Flag> flags) {
			this.flags.addAll(flags);
			return this;
		}

		/**
		 * @param type the type of data to add to this value
		 * @param data the value of the data to be added
		 * @return this
		 */
		@SafeVarargs
		public final <D> AbstractBuilder<T> with(DataType<D> type, D... data) {
			for (D d : data) {
				this.data.computeIfAbsent(type, t -> new ArrayList<>()).add(d);
			}

			return this;
		}

		/**
		 * @param type the type of data to add to this value
		 * @param data the value of the data to be added
		 * @return this
		 */
		public final <D> AbstractBuilder<T> with(DataType<D> type, Collection<D> data) {
			this.data.computeIfAbsent(type, t -> new ArrayList<>()).addAll(data);

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
		public final AbstractBuilder<T> with(BiConsumer<@Nullable T, T>... listeners) {
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
		 * previously present in the value container it's been added to.</p>
		 *
		 * @param listeners any number of listeners
		 * @return this
		 */
		@SafeVarargs
		public final AbstractBuilder<T> with(TriConsumer<@Nullable T, T, UUID>... listeners) {
			this.playerListeners.addAll(Arrays.asList(listeners));
			return this;
		}

		public abstract ValueKey<T> build();
	}

	public static class Builder<T> extends AbstractBuilder<T> {
		private final Collection<Constraint<T>> constraints = new ArrayList<>();

		/**
		 * @param defaultValue the value to be used when a config file doesn't exist or the value needs to be reset
		 */
		public Builder(@NotNull Supplier<@NotNull T> defaultValue) {
			super(defaultValue);
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

		public final ValueKey<T> build() {
			return new ValueKey<>(this.defaultValue, this.constraints, this.flags, this.data, this.listeners, this.playerListeners);
		}
	}

	public static class CollectionBuilder<T extends StronglyTypedImmutableCollection<?, V, ?>, V> extends AbstractBuilder<T> {
		protected final Collection<Constraint<V>> constraints = new ArrayList<>();
		protected final Collection<Constraint<T>> collectionConstraints = new ArrayList<>();

		/**
		 * @param defaultValue the value to be used when a config file doesn't exist or the value needs to be reset
		 */
		public CollectionBuilder(@NotNull Supplier<@NotNull T> defaultValue) {
			super(defaultValue);
		}

		/**
		 * @param constraints any number of constraints to be applied to the children of this config value
		 * @return this
		 */
		@SafeVarargs
		public final CollectionBuilder<T, V> constraint(Constraint<V>... constraints) {
			this.constraints.addAll(Arrays.asList(constraints));
			return this;
		}

		/**
		 * @param constraints any number of constraints to be applied to this config value
		 * @return this
		 */
		@SafeVarargs
		public final CollectionBuilder<T, V> collectionConstraint(Constraint<T>... constraints) {
			this.collectionConstraints.addAll(Arrays.asList(constraints));
			return this;
		}

		public ValueKey<T> build() {

			Collection<Constraint<T>> c = new ArrayList<>(this.collectionConstraints);

			c.add(new Constraint.Value<T, V>("fabric:compound_value", this.constraints));


			return new ValueKey<>(this.defaultValue, c, this.flags, this.data, this.listeners, this.playerListeners);
		}
	}

	public static class TableBuilder<T extends Table<V>, V> extends CollectionBuilder<T, V> {
		private final Collection<Constraint<String>> keyConstraints = new ArrayList<>();

		/**
		 * @param defaultValue the value to be used when a config file doesn't exist or the value needs to be reset
		 */
		public TableBuilder(@NotNull Supplier<@NotNull T> defaultValue) {
			super(defaultValue);
		}

		/**
		 * @param constraints any number of constraints to be applied to the keys of children of this config value
		 * @return this
		 */
		@SafeVarargs
		public final TableBuilder<T, V> keyConstraint(Constraint<String>... constraints) {
			this.keyConstraints.addAll(Arrays.asList(constraints));

			return this;
		}

		public ValueKey<T> build() {
			Collection<Constraint<T>> c = new ArrayList<>(this.collectionConstraints);

			c.add(new Constraint<T>("fabric:compound_value") {
				@Override
				public boolean passes(T value) {
					for (V v : value.getValues()) {
						for (Constraint<V> c : constraints) {
							if (!c.passes(v)) return false;
						}
					}

					return true;
				}

				@Override
				public void addLines(Consumer<String> linesConsumer) {
					constraints.forEach(constraint -> constraint.addLines(linesConsumer));
				}
			});

			c.add(new Constraint.Value<>("fabric:compound_value", this.constraints));
			c.add(new Constraint.Key<>("fabric:key_constraints", this.keyConstraints));

			return new ValueKey<>(this.defaultValue, c, this.flags, this.data, this.listeners, this.playerListeners);
		}
	}
}
