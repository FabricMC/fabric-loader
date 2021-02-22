package net.fabricmc.loader.api.config.util;

public interface TriConsumer<A, B, C> {
	void accept(A a, B b, C c);
}
