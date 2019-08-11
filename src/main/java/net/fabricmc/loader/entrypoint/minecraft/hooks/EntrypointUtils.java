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

package net.fabricmc.loader.entrypoint.minecraft.hooks;

import net.fabricmc.loader.FabricLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

final class EntrypointUtils {
	private EntrypointUtils() {

	}

	static <T> void logErrors(String name, Collection<T> entrypoints, Consumer<T> entrypointConsumer) {
		List<Throwable> errors = new ArrayList<>();

		FabricLoader.INSTANCE.getLogger().debug("Iterating over entrypoint '" + name + "'");

		entrypoints.forEach((e) -> {
			try {
				entrypointConsumer.accept(e);
			} catch (Throwable t) {
				errors.add(t);
			}
		});

		if (!errors.isEmpty()) {
			RuntimeException exception = new RuntimeException("Could not execute entrypoint stage '" + name + "' due to errors!");

			for (Throwable t : errors) {
				exception.addSuppressed(t);
			}
			
			throw exception;
		}
	}
}
