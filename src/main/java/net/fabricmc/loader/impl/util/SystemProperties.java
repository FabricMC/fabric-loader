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

package net.fabricmc.loader.impl.util;

public final class SystemProperties {
	// whether fabric loader is running in a development environment / mode, affects class path mod discovery, remapping, logging, ...
	public static final String DEVELOPMENT = "fabric.development";
	public static final String SIDE = "fabric.side";
	public static final String GAME_JAR_PATH = "fabric.gameJarPath";
	// set the game version for the builtin game mod/dependencies, bypassing auto-detection
	public static final String GAME_VERSION = "fabric.gameVersion";
	// fallback log file for the builtin log handler (dumped on exit if not replaced with another handler)
	public static final String LOG_FILE = "fabric.log.file";
	// minimum log level for builtin log handler
	public static final String LOG_LEVEL = "fabric.log.level";
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	public static final String ADD_MODS = "fabric.addMods";
	// file containing the class path for in-dev runtime mod remapping
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";
	// throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
	public static final String DEBUG_THROW_DIRECTLY = "fabric.debug.throwDirectly";
	// disables mod load order shuffling to be the same in-dev as in production
	public static final String DEBUG_DISABLE_MOD_SHUFFLE = "fabric.debug.disableModShuffle";
	// workaround for bad load order dependencies
	public static final String DEBUG_LOAD_LATE = "fabric.debug.loadLate";
	// override the mod discovery timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_DISCOVERY_TIMEOUT = "fabric.debug.discoveryTimeout";
	// override the mod resolution timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_RESOLUTION_TIMEOUT = "fabric.debug.resolutionTimeout";

	private SystemProperties() {
	}
}
