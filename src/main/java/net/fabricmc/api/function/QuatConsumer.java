package net.fabricmc.api.function;

@FunctionalInterface
public interface QuatConsumer<T, U, V, W> {
    void accept(T t, U u, V v, W w);
}
