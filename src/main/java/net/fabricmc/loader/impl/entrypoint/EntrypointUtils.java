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

package net.fabricmc.loader.impl.entrypoint;

import java.util.Collection;
import java.util.function.Consumer;

import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class EntrypointUtils {
	public static <T> void invoke(String name, Class<T> type, Consumer<? super T> invoker) {
		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;

		if (!loader.hasEntrypoints(name)) {
			Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", name);
		} else {
			invoke0(name, type, invoker);
		}
	}

	private static <T> void invoke0(String name, Class<T> type, Consumer<? super T> invoker) {
		RuntimeException exception = null;
		Collection<EntrypointContainer<T>> entrypoints = FabricLoaderImpl.INSTANCE.getEntrypointContainers(name, type);

		Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", name);

		for (EntrypointContainer<T> container : entrypoints) {
			try {
				invoker.accept(container.getEntrypoint());
			} catch (Throwable t) {
				exception = ExceptionUtil.gatherExceptions(t,
						exception,
						exc -> new RuntimeException(String.format("Could not execute entrypoint stage '%s' due to errors, provided by '%s'!",
								name, container.getProvider().getMetadata().getId()),
								exc));
			}
		}

		if (exception != null) {
			throw exception;
		}
	}
}
