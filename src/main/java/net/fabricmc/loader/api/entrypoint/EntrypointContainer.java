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

package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.ModContainer;

/**
 * Represents a container which holds both an entrypoint instance and the {@link ModContainer} which has provided the entrypoint,
 * @param <T> The type of the entrypoint
 */
public interface EntrypointContainer<T> {
	/**
	 * Gets the entrypoint.
	 * @return An entrypoint instance.
	 */
	T getEntrypoint();

	/**
	 * Gets the mod which has provided this entrypoint.
	 * @return The mod which as provided this entrypoint.
	 */
	ModContainer getProvider();
}