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

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;

/**
 * The public-facing FabricLoader instance.
 *
 * <p>To obtain a working instance, call {@link #getInstance()}.</p>
 *
 * @since 0.4.0
 */
public interface FabricLoader {
	/**
	 * Returns the public-facing Fabric Loader instance.
	 */
	static FabricLoader getInstance() {
		FabricLoader ret = FabricLoaderImpl.INSTANCE;

		if (ret == null) {
			throw new RuntimeException("Accessed FabricLoader too early!");
		}

		return ret;
	}

	/**
	 * Returns all entrypoints declared under a {@code key}, assuming they are of a specific type.
	 *
	 * @param key  the key in entrypoint declaration in {@code fabric.mod.json}
	 * @param type the type of entrypoints
	 * @param <T>  the type of entrypoints
	 * @return the obtained entrypoints
	 * @see #getEntrypointContainers(String, Class)
	 */
	<T> List<T> getEntrypoints(String key, Class<T> type);

	/**
	 * Returns all entrypoints declared under a {@code key}, assuming they are of a specific type.
	 *
	 * <p>The entrypoint is declared in the {@code fabric.mod.json} as following:
	 * <pre><blockquote>
	 *   "entrypoints": {
	 *     "&lt;a key&gt;": [
	 *       &lt;a list of entrypoint declarations&gt;
	 *     ]
	 *   }
	 * </blockquote></pre>
	 * Multiple keys can be present in the {@code entrypoints} section.</p>
	 *
	 * <p>An entrypoint declaration indicates that an arbitrary notation is sent
	 * to a {@link LanguageAdapter} to offer an instance of entrypoint. It is
	 * either a string, or an object. An object declaration
	 * is of this form:<pre><blockquote>
	 *   {
	 *     "adapter": "&lt;a custom adatper&gt;"
	 *     "value": "&lt;an arbitrary notation&gt;"
	 *   }
	 * </blockquote></pre>
	 * A string declaration {@code <an arbitrary notation>} is equivalent to
	 * <pre><blockquote>
	 *   {
	 *     "adapter": "default"
	 *     "value": "&lt;an arbitrary notation&gt;"
	 *   }
	 * </blockquote></pre>
	 * where the {@code default} adapter is the {@linkplain LanguageAdapter adapter}
	 * offered by Fabric Loader. </p>
	 *
	 * @param key  the key in entrypoint declaration in {@code fabric.mod.json}
	 * @param type the type of entrypoints
	 * @param <T>  the type of entrypoints
	 * @return the entrypoint containers related to this key
	 * @throws EntrypointException if a problem arises during entrypoint creation
	 * @see LanguageAdapter
	 */
	<T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

	/**
	 * Invokes an action on all entrypoints that would be returned by {@link #getEntrypointContainers(String, Class)} for the given
	 * <code>key</code> and <code>type</code>.
	 *
	 * <p>The action is invoked by applying the given <code>invoker</code> to each entrypoint instance.
	 *
	 * <p>Exceptions thrown by <code>invoker</code> will be collected and thrown after all entrypoints have been invoked.
	 *
	 * @param key     the key in entrypoint declaration in {@code fabric.mod.json}
	 * @param type    the type of entrypoints
	 * @param invoker applied to each entrypoint to invoke the desired action
	 * @param <T>     the type of entrypoints
	 * @see #getEntrypointContainers(String, Class)
	 */
	<T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker);

	/**
	 * Get the object share for inter-mod communication.
	 *
	 * <p>The share allows mods to exchange data without directly referencing each other. This makes simple interaction
	 * easier by eliminating any compile- or run-time dependencies if the shared value type is independent of the mod
	 * (only Java/game/Fabric types like collections, primitives, String, Consumer, Function, ...).
	 *
	 * <p>Active interaction is possible as well since the shared values can be arbitrary Java objects. For example
	 * exposing a {@code Runnable} or {@code Function} allows the "API" user to directly invoke some program logic.
	 *
	 * <p>It is required to prefix the share key with the mod id like {@code mymod:someProperty}. Mods should not
	 * modify entries by other mods. The share is thread safe.
	 *
	 * @return the global object share instance
	 * @since 0.12.0
	 */
	ObjectShare getObjectShare();

	/**
	 * Get the current mapping resolver.
	 *
	 * <p>When performing reflection, a mod should always query the mapping resolver for
	 * the remapped names of members than relying on other heuristics.</p>
	 *
	 * @return the current mapping resolver instance
	 * @since 0.4.1
	 */
	MappingResolver getMappingResolver();

	/**
	 * Gets the container for a given mod.
	 *
	 * @param id the ID of the mod
	 * @return the mod container, if present
	 */
	Optional<ModContainer> getModContainer(String id);

	/**
	 * Gets all mod containers.
	 *
	 * @return a collection of all loaded mod containers
	 */
	Collection<ModContainer> getAllMods();

	/**
	 * Checks if a mod with a given ID is loaded.
	 *
	 * @param id the ID of the mod, as defined in {@code fabric.mod.json}
	 * @return whether or not the mod is present in this Fabric Loader instance
	 */
	boolean isModLoaded(String id);

	/**
	 * Checks if Fabric Loader is currently running in a "development"
	 * environment. Can be used for enabling debug mode or additional checks.
	 *
	 * <p>This should not be used to make assumptions on certain features,
	 * such as mappings, but as a toggle for certain functionalities.</p>
	 *
	 * @return whether or not Loader is currently in a "development"
	 * environment
	 */
	boolean isDevelopmentEnvironment();

	/**
	 * Get the current environment type.
	 *
	 * @return the current environment type
	 */
	EnvType getEnvironmentType();

	/**
	 * Get the original unprocessed game version.
	 *
	 * <p>There is normally a Semver-compatible derived game version, obtainable from a mod container representing the
	 * game, that is derived from this raw version. The raw version may not comparable or even follow a well defined
	 * pattern, making it unusable for dependency range evaluation.
	 */
	String getRawGameVersion();

	/**
	 * Get the current game instance. Can represent a game client or
	 * server object. As such, the exact return is dependent on the
	 * current environment type.
	 *
	 * <p>The game instance may not always be available depending on the game version and {@link EnvType environment}.
	 *
	 * @return A client or server instance object
	 * @deprecated This method is experimental and its use is discouraged.
	 */
	/* @Nullable */
	@Deprecated
	Object getGameInstance();

	/**
	 * Get the current game working directory.
	 *
	 * @return the working directory
	 */
	Path getGameDir();

	@Deprecated
	File getGameDirectory();

	/**
	 * Get the current directory for game configuration files.
	 *
	 * @return the configuration directory
	 */
	Path getConfigDir();

	@Deprecated
	File getConfigDirectory();

	/**
	 * Gets the command line arguments used to launch the game.
	 *
	 * <p>The implementation will try to strip or obscure sensitive data like authentication tokens if {@code sanitize}
	 * is set to true. Callers are highly encouraged to enable sanitization as compromising the information can easily
	 * happen with logging, exceptions, serialization or other causes.
	 *
	 * <p>There is no guarantee that {@code sanitize} covers everything, so the launch arguments should still not be
	 * logged or otherwise exposed routinely even if the parameter is set to {@code true}. In particular it won't
	 * necessarily strip all information that can be used to identify the user.
	 *
	 * @param sanitize Whether to try to remove or obscure sensitive information
	 * @return the launch arguments for the game
	 */
	String[] getLaunchArguments(boolean sanitize);
}
