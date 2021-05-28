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

package net.fabricmc.loader.util;

public final class SystemProperties {
	public static final String DEVELOPMENT = "fabric.development";
	public static final String SIDE = "fabric.side";
	public static final String GAME_JAR_PATH = "fabric.gameJarPath";
	public static final String GAME_VERSION = "fabric.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";

	private SystemProperties() {
	}
}
