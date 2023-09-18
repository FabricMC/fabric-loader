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

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.function.Consumer;

import net.fabricmc.loader.api.EntrypointException;

import org.spongepowered.asm.util.perf.Profiler;

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

		Profiler profiler = new Profiler("entrypoints");
		Log.info(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s' with %s entrypoints", name, entrypoints.size());
		profiler.mark("entrypoinscan");
		Profiler.Section timer = profiler.begin("entrypoint");

		for (EntrypointContainer<T> container : entrypoints) {

			boolean hasLink = container.getProvider().getMetadata().getContact().get("sources").isPresent() && container.getProvider().getMetadata().getContact().get("sources").get().length() > 0;
			String sourcesUrl = !hasLink ? "" : " at "+container.getProvider().getMetadata().getContact().get("sources").get();

			Log.trace(LogCategory.ENTRYPOINT, "invoke %s on '%s'%s", name, container.getProvider().getMetadata().getName(), sourcesUrl);
			try {
				invoker.accept(container.getEntrypoint());
			} catch (Throwable t) {
				Throwable root = t;
				if (root instanceof EntrypointException) {
					root = t.getCause();
				}
				Log.error(LogCategory.ENTRYPOINT, "Exception during %s : '%s' : %s", name, container.getProvider().getMetadata().getName(), root);
				exception = ExceptionUtil.gatherExceptions(t,
						exception,
						exc -> new RuntimeException(String.format("Could not execute entrypoint stage '%s' due to errors, provided by '%s'!",
								name, container.getProvider().getMetadata().getId()),
								exc));
			}
		}
		timer.end();
		long elapsedMs = timer.getTime();
		double elapsedTime = timer.getSeconds();
		String elapsed = new DecimalFormat("###0.000").format(elapsedTime);
		final int total = entrypoints.size();
		String perMixinTime = new DecimalFormat("###0.0").format(((double)elapsedMs) / total);
		Log.info(LogCategory.ENTRYPOINT, "Entrypoint scan %s with %s points %s sec (%sms avg)", name, total, elapsed, perMixinTime);
		/**
		  NOTE: this scan isn't a true representation of how long the Mixing takes for the preLaunch phase!!
		 WHY? I DON'T KNOW.
		  the "Entrypoint scan" messages appears after 1-3s, and then there is 13s of Sponge Mixing.
		 "Entrypoint scan" messages for "main" and "client" appear AFTER Sponge is done Mixing, showing grouped duration of 10-30s for entrypoint loop (above) and Sponge Mixing.
		 * the next phase after this is {@linkplain FabricLoaderImpl#prepareModInit(Path, Object)}
		 */

		if (exception != null) {
			throw exception;
		}
	}
}
