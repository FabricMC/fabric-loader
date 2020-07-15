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

package net.fabricmc.loader.discovery;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class DirectoryModCandidateFinder implements ModCandidateFinder {
	private final Path path;

	public DirectoryModCandidateFinder(Path path) {
		this.path = path;
	}

	@Override
	public void findCandidates(FabricLoader loader, Consumer<URL> urlProposer) {
		if (!Files.exists(path)) {
			try {
				Files.createDirectory(path);
			} catch (IOException e) {
				throw new RuntimeException("Could not create directory " + path, e);
			}
		}

		if (!Files.isDirectory(path)) {
			throw new RuntimeException(path + " is not a directory!");
		}

		try {
			Files.walk(path, 1, FileVisitOption.FOLLOW_LINKS).forEach((modPath) -> {
				if (!Files.isDirectory(modPath) && modPath.toString().endsWith(".jar")) {
					try {
						urlProposer.accept(UrlUtil.asUrl(modPath));
					} catch (UrlConversionException e) {
						throw new RuntimeException("Failed to convert URL for mod '" + modPath + "'!", e);
					}
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Exception while searching for mods in '" + path + "'!", e);
		}
	}
}
