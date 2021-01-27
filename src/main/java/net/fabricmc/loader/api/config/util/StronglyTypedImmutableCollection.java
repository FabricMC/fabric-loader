package net.fabricmc.loader.api.config.util;

import java.util.function.Supplier;

public abstract class StronglyTypedImmutableCollection<K, V, I> implements Iterable<I>, ValueCollection<V> {
    protected final Class<V> valueClass;
    protected final Supplier<V> defaultValue;

    public StronglyTypedImmutableCollection(Class<V> valueClass, Supplier<V> defaultValue) {
        this.valueClass = valueClass;
        this.defaultValue = defaultValue;
    }

    public final Class<V> getValueClass() {
        return this.valueClass;
    }

	/**
	 * @return a new instance of this class, with space for an additional entry appended to it
	 */
    public abstract StronglyTypedImmutableCollection<K, V, I> addEntry();
    public abstract V get(K key);

    public Supplier<V> getDefaultValue() {
        return this.defaultValue;
    }

	/**
	 * Creates a new instance of this class, with the value at key set to v.
	 * @param key the key to change
	 * @param v the new value
	 * @return a new instance of this class
	 */
    public abstract StronglyTypedImmutableCollection<K, V, I> set(K key, V v);

    public abstract StronglyTypedImmutableCollection<K, V, I> remove(int index);

    public abstract int size();
}
