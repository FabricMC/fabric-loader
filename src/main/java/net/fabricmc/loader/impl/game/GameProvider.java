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

package net.fabricmc.loader.impl.game;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.LoaderUtil;

public interface GameProvider extends GameDefinition { // name directly referenced in net.fabricmc.loader.impl.launch.knot.Knot.findEmbedddedGameProvider() and service loader records

	String getEntrypoint();
	boolean isObfuscated();
	boolean requiresUrlClassLoader();

	boolean isEnabled();
	boolean locateGame(FabricLauncher launcher, String[] args);
	void initialize(FabricLauncher launcher);
	GameTransformer getEntrypointTransformer();
	void unlockClassPath(FabricLauncher launcher);
	void launch(ClassLoader loader);

	default boolean displayCrash(Throwable exception, String context) {
		return false;
	}

	Arguments getArguments();
	String[] getLaunchArguments(boolean sanitize);

	default boolean canOpenErrorGui() {
		return true;
	}

	default boolean hasAwtSupport() {
		return LoaderUtil.hasAwtSupport();
	}

	class BuiltinMod {
		public BuiltinMod(List<Path> paths, ModMetadata metadata) {
			Objects.requireNonNull(paths, "null paths");
			Objects.requireNonNull(metadata, "null metadata");

			this.paths = paths;
			this.metadata = metadata;
		}

		public final List<Path> paths;
		public final ModMetadata metadata;
	}
}
