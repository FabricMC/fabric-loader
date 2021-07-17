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
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.util.Arguments;

public interface GameProvider {
	String getGameId();
	String getGameName();
	String getRawGameVersion();
	String getNormalizedGameVersion();
	Collection<BuiltinMod> getBuiltinMods();

	String getEntrypoint();
	Path getLaunchDirectory();
	boolean isObfuscated();
	boolean requiresUrlClassLoader();
	List<Path> getGameContextJars();

	boolean locateGame(EnvType envType, String[] args, ClassLoader loader);
	GameTransformer getEntrypointTransformer();
	void launch(ClassLoader loader);

	Arguments getArguments();
	String[] getLaunchArguments(boolean sanitize);

	default boolean canOpenErrorGui() {
		return true;
	}

	class BuiltinMod {
		public BuiltinMod(Path path, ModMetadata metadata) {
			Objects.requireNonNull(path, "null path");
			Objects.requireNonNull(metadata, "null metadata");

			this.path = path;
			this.metadata = metadata;
		}

		public final Path path;
		public final ModMetadata metadata;
	}
}
