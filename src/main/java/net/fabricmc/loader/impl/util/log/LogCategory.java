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

package net.fabricmc.loader.impl.util.log;

public final class LogCategory {
	public static final LogCategory DISCOVERY = new LogCategory("Discovery");
	public static final LogCategory ENTRYPOINT = new LogCategory("Entrypoint");
	public static final LogCategory GAME_PATCH = new LogCategory("GamePatch");
	public static final LogCategory GAME_PROVIDER = new LogCategory("GameProvider");
	public static final LogCategory GAME_REMAP = new LogCategory("GameRemap");
	public static final LogCategory GENERAL = new LogCategory();
	public static final LogCategory KNOT = new LogCategory("Knot");
	public static final LogCategory LOG = new LogCategory("Log");
	public static final LogCategory MAPPINGS = new LogCategory("Mappings");
	public static final LogCategory METADATA = new LogCategory("Metadata");
	public static final LogCategory MOD_REMAP = new LogCategory("ModRemap");
	public static final LogCategory MIXIN = new LogCategory("Mixin");
	public static final LogCategory RESOLUTION = new LogCategory("Resolution");
	public static final LogCategory TEST = new LogCategory("Test");

	public final String name;
	public Object data;

	public LogCategory(String... names) {
		this.name = String.join("/", names);
	}
}
