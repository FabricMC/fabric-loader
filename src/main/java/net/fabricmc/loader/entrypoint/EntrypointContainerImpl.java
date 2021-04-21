/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.entrypoint;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.function.Supplier;

public class EntrypointContainerImpl<T> implements EntrypointContainer<T> {
	private final ModContainer container;
	private final Supplier<T> entrypointSupplier;
	private T instance;

	public EntrypointContainerImpl(ModContainer container, Supplier<T> entrypointSupplier) {
		this.container = container;
		this.entrypointSupplier = entrypointSupplier;
	}

	@Override
	public synchronized T getEntrypoint() {
		if (instance == null) {
			this.instance = entrypointSupplier.get();
		}

		return instance;
	}

	@Override
	public ModContainer getProvider() {
		return container;
	}
}
