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

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The public-facing FabricLoader instance.
 * @since 0.4.0
 */
public interface FabricLoader {
	@SuppressWarnings("deprecation")
	static FabricLoader getInstance() {
		if (net.fabricmc.loader.FabricLoader.INSTANCE == null) {
			throw new RuntimeException("Accessed FabricLoader too early!");
		}

		return net.fabricmc.loader.FabricLoader.INSTANCE;
	}

	<T> List<T> getEntrypoints(String key, Class<T> type);

	<T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

	/**
	 * Get the current mapping resolver.
	 * @return The current mapping resolver instance.
	 * @since 0.4.1
	 */
	MappingResolver getMappingResolver();

	/**
	 * Gets the container for a given mod.
	 * @param id The ID of the mod.
	 * @return The mod container, if present.
	 */
	Optional<ModContainer> getModContainer(String id);

	/**
	 * Gets all mod containers.
	 * @return A collection of all loaded mod containers.
	 */
	Collection<ModContainer> getAllMods();

	/**
	 * Checks if a mod with a given ID is loaded.
	 * @param id The ID of the mod, as defined in fabric.mod.json.
	 * @return Whether or not the mod is present in this FabricLoader instance.
	 */
	boolean isModLoaded(String id);

	/**
	 * Checks if Fabric Loader is currently running in a "development"
	 * environment. Can be used for enabling debug mode or additional checks.
	 * Should not be used to make assumptions about f.e. mappings.
	 * @return Whether or not Loader is currently in a "development"
	 * environment.
	 */
	boolean isDevelopmentEnvironment();

	/**
	 * Get the current environment type.
	 * @return The current environment type.
	 */
	EnvType getEnvironmentType();

	/**
	 * Get the current game instance. Can represent a game client or
	 * server object. As such, the exact return is dependent on the
	 * current environment type.
	 * @return A client or server instance object.
	 */
	Object getGameInstance();

	/**
	 * Get the current game working directory.
	 * @return The directory.
	 */
	Path getGameDir();

	@Deprecated
	File getGameDirectory();

	/**
	 * Get the current directory for game configuration files.
	 * @return The directory.
	 */
	Path getConfigDir();

	@Deprecated
	File getConfigDirectory();
}
