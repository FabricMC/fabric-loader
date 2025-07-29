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

import java.nio.file.Path;

/**
 * Provides access to cache or data directories for a mod.
 *
 * <p>These directories are unique to the mod id and should not be shared between mods. These directories are not versioned so
 * special care must be taken to ensure backwards compatibility with older versions of the mod running on the same machine.
 *
 * <p>Global directories are shared between all instances of Fabric Loader for the current user of the machine, no matter the game or launcher.
 *
 * <p>The cache directories should be used for temporary files that can be regenerated if lost.
 *
 * <p>Any sensitive data should be encrypted, and the private key stored in the {@link #getGlobalDataDir()}.
 *
 * <p>A sandbox implementation will grant full access to all of these directories, and may not isolate them from other instances.
 * Code should not be stored and executed from these directories to prevent a sandbox escape.
 */
public interface ModDirectories {
	/**
	 * Get the local cache directory for the mod.
	 *
	 * <p>Note: This directory should not be distributed, for example in a mod pack.
	 *
	 * @return A {@link Path} to the cache directory.
	 */
	Path getCacheDir();

	/**
	 * Get the global cache directory for the mod.
	 *
	 * @return A {@link Path} to the global cache directory.
	 */
	Path getGlobalCacheDir();

	/**
	 * Get the local data directory for the mod.
	 *
	 * @return A {@link Path} to the data directory.
	 */
	Path getDataDir();

	/**
	 * Get the global data directory for the mod.
	 *
	 * @return A {@link Path} to the global data directory.
	 */
	Path getGlobalDataDir();
}
