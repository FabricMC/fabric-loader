package net.fabricmc.loader.api.config.util;

import java.util.Arrays;
import java.util.function.Supplier;

public abstract class StronglyTypedImmutableList<T, I> extends StronglyTypedImmutableCollection<Integer, T, I> {
    protected final T[] values;

    @SafeVarargs
    public StronglyTypedImmutableList(Class<T> valueClass, Supplier<T> defaultValue, T... values) {
        super(valueClass, defaultValue);
        this.values = values;
    }

    public StronglyTypedImmutableList(StronglyTypedImmutableList<T, I> other) {
        super(other.valueClass, other.defaultValue);
        this.values = Arrays.copyOf(other.values, other.values.length);
    }

    @Override
    public T get(Integer key) {
        return this.values[key];
    }

    @Override
    public int size() {
        return this.values.length;
    }
}
