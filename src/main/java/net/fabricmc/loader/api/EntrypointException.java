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

package net.fabricmc.loader.api;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Exception thrown when creating an entrypoint fails
 * 
 * @see FabricLoader#getEntrypoints(String, Class)
 * @see FabricLoader#getEntrypointContainers(String, Class)
 * 
 * @since 0.9
 */
public abstract class EntrypointException extends RuntimeException {
	private static final long serialVersionUID = -1219519081530919388L;

	protected EntrypointException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * The entrypoint key which was being constructed (such as {@code init} or {@code client})
	 * 
	 * @return The entrypoint key which the entry was registered for
	 */
	public abstract String getEntrypointName();

	/**
	 * The owning mod of the entry which failed to be constructed
	 * 
	 * @return The owning mod of the entry
	 */
	public abstract ModContainer getOwningMod();

	/**
	 * Find any additional exceptions from other entries which were also thrown after this
	 * 
	 * @return Additional exceptions which were thrown for the same entrypoint
	 */
	public Stream<EntrypointException> getFurtherExceptions() {
		return Arrays.stream(getSuppressed()).filter(e -> e instanceof EntrypointException).map(e -> (EntrypointException) e);
	}
}