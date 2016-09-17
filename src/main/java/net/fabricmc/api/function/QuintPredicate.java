package net.fabricmc.api.function;

@FunctionalInterface
public interface QuintPredicate<T, U, V, W, X> {
    boolean test(T t, U u, V v, W w, X x);
}
