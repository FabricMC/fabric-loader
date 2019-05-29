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

package net.fabricmc.loader;

import net.fabricmc.loader.api.EntrypointContainer;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

final class ModEntrypointContainer<T> implements EntrypointContainer<T> {
	private final T object;
	private final Optional<ModContainer> provider;
	private final String name;

	ModEntrypointContainer(T object, ModContainer provider, String name) {
		this.object = object;
		this.provider = Optional.of(provider);
		this.name = name;
	}

	@Override
	public T get() {
		return object;
	}

	@Override
	public Optional<ModContainer> getProvidingMod() {
		return provider;
	}

	@Override
	public String getName() {
		return name;
	}
}
