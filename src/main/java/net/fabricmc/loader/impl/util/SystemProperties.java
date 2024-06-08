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
	// skips the embedded MC game provider, letting ServiceLoader-provided ones take over
	public static final String SKIP_MC_PROVIDER = "fabric.skipMcProvider";
	// game jar paths for common/client/server, replaces lookup from class path if present, env specific takes precedence
	public static final String GAME_JAR_PATH = "fabric.gameJarPath";
	public static final String GAME_JAR_PATH_CLIENT = "fabric.gameJarPath.client";
	public static final String GAME_JAR_PATH_SERVER = "fabric.gameJarPath.server";
	// set the game version for the builtin game mod/dependencies, bypassing auto-detection
	public static final String GAME_VERSION = "fabric.gameVersion";
	// fallback log file for the builtin log handler (dumped on exit if not replaced with another handler)
	public static final String LOG_FILE = "fabric.log.file";
	// minimum log level for builtin log handler
	public static final String LOG_LEVEL = "fabric.log.level";
	// a path to a directory to replace the default mod search directory
	public static final String MODS_FOLDER = "fabric.modsFolder";
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	public static final String ADD_MODS = "fabric.addMods";
	// a comma-separated list of mod ids to disable, even if they're discovered. mostly useful for unit testing.
	public static final String DISABLE_MOD_IDS = "fabric.debug.disableModIds";
	// file containing the class path for in-dev runtime mod remapping
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";
	// class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
	public static final String PATH_GROUPS = "fabric.classPathGroups";
	// enable the fixing of package access errors in the game jar(s)
	public static final String FIX_PACKAGE_ACCESS = "fabric.fixPackageAccess";
	// system level libraries, matching code sources will not be assumed to be part of the game or mods and remain on the system class path (paths separated by path separator)
	public static final String SYSTEM_LIBRARIES = "fabric.systemLibraries";
	// throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
	public static final String DEBUG_THROW_DIRECTLY = "fabric.debug.throwDirectly";
	// logs library classification activity
	public static final String DEBUG_LOG_LIB_CLASSIFICATION = "fabric.debug.logLibClassification";
	// logs class loading
	public static final String DEBUG_LOG_CLASS_LOAD = "fabric.debug.logClassLoad";
	// logs class loading errors to uncover caught exceptions without adequate logging
	public static final String DEBUG_LOG_CLASS_LOAD_ERRORS = "fabric.debug.logClassLoadErrors";
	// logs class transformation errors to uncover caught exceptions without adequate logging
	public static final String DEBUG_LOG_TRANSFORM_ERRORS = "fabric.debug.logTransformErrors";
	// disables system class path isolation, allowing bogus lib accesses (too early, transient jars)
	public static final String DEBUG_DISABLE_CLASS_PATH_ISOLATION = "fabric.debug.disableClassPathIsolation";
	// disables mod load order shuffling to be the same in-dev as in production
	public static final String DEBUG_DISABLE_MOD_SHUFFLE = "fabric.debug.disableModShuffle";
	// workaround for bad load order dependencies
	public static final String DEBUG_LOAD_LATE = "fabric.debug.loadLate";
	// override the mod discovery timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_DISCOVERY_TIMEOUT = "fabric.debug.discoveryTimeout";
	// override the mod resolution timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_RESOLUTION_TIMEOUT = "fabric.debug.resolutionTimeout";
	// replace mod versions (modA:versionA,modB:versionB,...)
	public static final String DEBUG_REPLACE_VERSION = "fabric.debug.replaceVersion";
	// deobfuscate the game jar with the classpath
	public static final String DEBUG_DEOBFUSCATE_WITH_CLASSPATH = "fabric.debug.deobfuscateWithClasspath";
	// whether fabric loader is running in a unit test, this affects logging classpath setup
	public static final String UNIT_TEST = "fabric.unitTest";

	private SystemProperties() {
	}
}
