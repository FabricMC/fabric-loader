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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import net.fabricmc.loader.api.ModDirectories;

public final class ModDirectoriesImpl implements ModDirectories {
	private final String modId;
	private final Path gameDir;
	private final GlobalDirectories globalDirectories;

	public ModDirectoriesImpl(String modId, Path gameDir, GlobalDirectories globalDirectories) {
		this.modId = Objects.requireNonNull(modId);
		this.gameDir = gameDir;
		this.globalDirectories = Objects.requireNonNull(globalDirectories);
	}

	@Override
	public Path getCacheDir() {
		return ensureDir(gameDir.resolve("cache").resolve(modId));
	}

	@Override
	public Path getGlobalCacheDir() {
		return ensureDir(globalDirectories.getGlobalCacheRoot().resolve(modId));
	}

	@Override
	public Path getDataDir() {
		return ensureDir(gameDir.resolve("fabric").resolve(modId));
	}

	@Override
	public Path getGlobalDataDir() {
		return ensureDir(globalDirectories.getGlobalDataRoot().resolve(modId));
	}

	private Path ensureDir(Path path) {
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (IOException e) {
				throw new RuntimeException("Failed to create directory: " + path, e);
			}
		}

		return path;
	}
}
