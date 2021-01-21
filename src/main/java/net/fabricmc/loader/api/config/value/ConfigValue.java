package net.fabricmc.loader.api.config.value;

import com.google.common.collect.*;
import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import net.fabricmc.loader.config.ConfigManagerImpl;
import net.fabricmc.loader.config.ValueContainerProviders;
import net.fabricmc.loader.config.ValueKey;
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
public class ConfigValue<T> implements Comparable<ConfigValue<?>> {
    private final Supplier<T> defaultValue;
    private final ImmutableCollection<Constraint<T>> constraints;
	private final ImmutableCollection<Flag> flags;
	private final Multimap<DataType<?>, Object> data;
	private final ImmutableCollection<BiConsumer<T, T>> listeners;
	private final ImmutableCollection<TriConsumer<T, T, UUID>> playerListeners;

	private ValueKey key;

    private ConfigValue(@NotNull Supplier<@NotNull T> defaultValue, Collection<Constraint<T>> constraints, Collection<Flag> flags, Multimap<DataType<?>, Object> data, List<BiConsumer<T, T>> listeners, List<TriConsumer<T, T, UUID>> playerListeners) {
        this.defaultValue = defaultValue;
		this.constraints = ImmutableList.copyOf(constraints);
		this.flags = ImmutableSet.copyOf(flags);

		this.data = LinkedHashMultimap.create();
		this.data.putAll(data);

		this.listeners = ImmutableList.copyOf(listeners);
		this.playerListeners = ImmutableList.copyOf(playerListeners);
	}

    /**
     * Gets the default value of this config value for initial population of config files and reset purposes.
     * @return the default value of this config value
     */
    public T getDefaultValue() {
        return this.defaultValue.get();
    }

    @Override
    public int compareTo(@NotNull ConfigValue<?> o) {
        return this.key.compareTo(o.key);
    }

    public T get() {
        if (this.key == null) {
            throw new ConfigValueException("Key not properly set for " + this.toString());
        }

        ValueContainerProvider provider = ValueContainerProviders.getInstance(this.key.getConfig().getSaveType());

        return provider.getValueContainer().get(this);
    }

	public T get(UUID playerId) {
		if (this.key == null) {
			throw new ConfigValueException("Key not properly set for " + this.toString());
		}

		ValueContainerProvider provider = ValueContainerProviders.getInstance(this.key.getConfig().getSaveType());

		return provider.getPlayerValueContainer(playerId).get(this);
	}

	public T set(T newValue) {
		if (this.key == null) {
			throw new ConfigValueException("Key not properly set for " + this.toString());
		}

		ValueContainer valueContainer = ValueContainerProviders.getInstance(this.key.getConfig().getSaveType()).getValueContainer();

		if (!isWithinConstraints(newValue)) {
			throw new ConfigValueException("Value '" + newValue + "' is not within constraints for key '" + this.key.toString() + "'");
		}

		T oldValue = valueContainer.put(this, newValue);

		this.listeners.forEach(listener -> listener.accept(oldValue, newValue));

		return oldValue;
	}

	public T set(UUID playerId, T newValue) {
		if (this.key == null) {
			throw new ConfigValueException("Key not properly set for " + this.toString());
		}

		ValueContainer valueContainer = ValueContainerProviders.getInstance(this.key.getConfig().getSaveType()).getPlayerValueContainer(playerId);

		if (!isWithinConstraints(newValue)) {
			throw new ConfigValueException("Value is not within constraints");
		}

		T oldValue = valueContainer.put(this, newValue);

		this.playerListeners.forEach(listener -> listener.accept(oldValue, newValue, playerId));

		return oldValue;
	}

	public T set(T newValue, ValueContainer valueContainer) {
		if (this.key == null) {
			throw new ConfigValueException("Key not properly set for " + this.toString());
		}

		if (!isWithinConstraints(newValue)) {
			throw new ConfigValueException("Value '" + newValue + "' is not within constraints for key '" + this.key.toString() + "'");
		}

		T oldValue = valueContainer.put(this, newValue);

		this.listeners.forEach(listener -> listener.accept(oldValue, newValue));

		return oldValue;
	}

	public boolean isWithinConstraints(T value) {
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
		return (Iterable<D>) this.data.get(dataType);
	}

    public ValueKey getKey() {
        return this.key;
    }

	/**
	 * Sets the key that can be used to refer to this config value.
	 * Must be initialized lazily so that different config providers can derive the keys in different ways.
	 * @param valueKey the key used to represent this config value
	 * @return this
	 */
	@ApiStatus.Internal
	public ConfigValue<T> setKey(ValueKey valueKey) {
		if (this.key != null || ConfigManagerImpl.isFinished()) {
			throw new ConfigValueException("Attempted to set key after configs have finished being registered");
		}

		this.key = valueKey;
		return this;
	}

    public static class Builder<T> {
		private final Supplier<T> defaultValue;
		private final Collection<Constraint<T>> constraints = new ArrayList<>();
		private final List<Flag> flags = new ArrayList<>();
		private final Multimap<DataType<?>, Object> data = LinkedHashMultimap.create();
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
			this.data.put(type, data);
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

		public final ConfigValue<T> build() {
			return new ConfigValue<>(this.defaultValue, this.constraints, this.flags, this.data, this.listeners, this.playerListeners);
		}
	}
}
