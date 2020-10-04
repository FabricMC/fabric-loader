package net.fabricmc.loader.entrypoint;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.LazyEntrypointContainer;

import java.util.function.Supplier;

public class LazyEntrypointContainerImpl<T> implements LazyEntrypointContainer<T> {
	private final ModContainer container;
	private final Supplier<T> entrypointSupplier;

	public LazyEntrypointContainerImpl(ModContainer container, Supplier<T> entrypointSupplier) {
		this.container = container;
		this.entrypointSupplier = entrypointSupplier;
	}

	@Override
	public T createEntrypoint() throws Exception {
		try {
			return entrypointSupplier.get();
		} catch (RuntimeException ex) {
			throw (Exception) ex.getCause();
		}
	}

	@Override
	public ModContainer getProvider() {
		return container;
	}
}
