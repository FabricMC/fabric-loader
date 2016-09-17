package net.fabricmc.api.function;

@FunctionalInterface
public interface QuatPredicate<T, U, V, W> {
    boolean test(T t, U u, V v, W w);
}
