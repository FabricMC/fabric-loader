package net.fabricmc.api.function;

@FunctionalInterface
public interface SextPredicate<T, U, V, W, X, Y> {
    boolean test(T t, U u, V v, W w, X x, Y y);
}
