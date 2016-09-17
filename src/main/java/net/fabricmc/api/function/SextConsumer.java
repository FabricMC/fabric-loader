package net.fabricmc.api.function;

@FunctionalInterface
public interface SextConsumer<T, U, V, W, X, Y> {
    void accept(T t, U u, V v, W w, X x, Y y);
}
