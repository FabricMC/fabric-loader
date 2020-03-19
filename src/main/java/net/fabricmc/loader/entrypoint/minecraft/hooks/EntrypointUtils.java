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
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class EntrypointUtils {
	public static <T> void invoke(String name, Class<T> type, Consumer<? super T> invoker) {
		@SuppressWarnings("deprecation")
		FabricLoader loader = FabricLoader.INSTANCE;

		if (!loader.hasEntrypoints(name)) {
			loader.getLogger().debug("No subscribers for entrypoint '" + name + "'");
		} else {
			invoke0(name, type, invoker);
		}
	}

	private static <T> void invoke0(String name, Class<T> type, Consumer<? super T> invoker) {
		@SuppressWarnings("deprecation")
		FabricLoader loader = FabricLoader.INSTANCE;
		RuntimeException exception = null;
		Collection<EntrypointContainer<T>> entrypoints = loader.getEntrypointContainers(name, type);

		loader.getLogger().debug("Iterating over entrypoint '" + name + "'");

		for (EntrypointContainer<T> container : entrypoints) {
			try {
				invoker.accept(container.getEntrypoint());
			} catch (Throwable t) {
				if (exception == null) {
					exception = new RuntimeException("Could not execute entrypoint stage '" + name + "' due to errors, provided by '" + container.getProvider().getMetadata().getId() + "'!", t);
				} else {
					exception.addSuppressed(t);
				}
			}
		}

		if (exception != null) {
			throw exception;
		}
	}
}
