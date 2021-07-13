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

package net.fabricmc.loader.impl.discovery;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class ArgumentModCandidateFinder implements ModCandidateFinder {
	private final boolean requiresRemap;

	public ArgumentModCandidateFinder(boolean requiresRemap) {
		this.requiresRemap = requiresRemap;
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
		String list = System.getProperty(SystemProperties.ADD_MODS);
		if (list != null) addMods(list, "system property", out);

		list = FabricLoaderImpl.INSTANCE.getGameProvider().getArguments().remove(Arguments.ADD_MODS);
		if (list != null) addMods(list, "argument", out);
	}

	private void addMods(String list, String source, ModCandidateConsumer out) {
		for (String pathStr : list.split(File.pathSeparator)) {
			if (pathStr.isEmpty()) continue;

			if (pathStr.startsWith("@")) {
				Path path = Paths.get(pathStr.substring(1));

				if (!Files.isRegularFile(path)) {
					Log.warn(LogCategory.DISCOVERY, "Missing/invalid %s provided mod list file %s", source, path);
					continue;
				}

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					String fileSource = String.format("%s file %s", source, path);
					String line;

					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty()) continue;

						addMod(line, fileSource, out);
					}
				} catch (IOException e) {
					throw new RuntimeException(String.format("Error reading %s provided mod list file %s", source, path), e);
				}
			} else {
				addMod(pathStr, source, out);
			}
		}
	}

	private void addMod(String pathStr, String source, ModCandidateConsumer out) {
		Path path = Paths.get(pathStr).toAbsolutePath().normalize();

		if (!Files.exists(path)) { // missing
			Log.warn(LogCategory.DISCOVERY, "Missing %s provided mod path %s", source, path);
		} else if (Files.isDirectory(path)) { // directory for extracted mod (in-dev usually) or jars (like mods, but recursive)
			if (isHidden(path)) {
				Log.warn(LogCategory.DISCOVERY, "Ignoring hidden %s provided mod path %s", source, path);
				return;
			}

			if (Files.exists(path.resolve("fabric.mod.json"))) { // extracted mod
				out.accept(path, requiresRemap);
			} else { // dir containing jars
				try {
					List<String> skipped = new ArrayList<>();

					Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							if (DirectoryModCandidateFinder.isValidFile(file)) {
								out.accept(file, requiresRemap);
							} else {
								skipped.add(path.relativize(file).toString());
							}

							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
							if (isHidden(dir)) {
								return FileVisitResult.SKIP_SUBTREE;
							} else {
								return FileVisitResult.CONTINUE;
							}
						}
					});

					if (!skipped.isEmpty()) {
						Log.warn(LogCategory.DISCOVERY, "Incompatible files in %s provided mod directory %s (non-jar or hidden): %s", source, path, String.join(", ", skipped));
					}
				} catch (IOException e) {
					Log.warn(LogCategory.DISCOVERY, "Error processing %s provided mod path %s: %s", source, path, e);
				}
			}
		} else { // single file
			if (!DirectoryModCandidateFinder.isValidFile(path)) {
				Log.warn(LogCategory.DISCOVERY, "Incompatible file in %s provided mod path %s (non-jar or hidden)", source, path);
			} else {
				out.accept(path, requiresRemap);
			}
		}
	}

	private static boolean isHidden(Path path) {
		try {
			return path.getFileName().toString().startsWith(".") || Files.isHidden(path);
		} catch (IOException e) {
			Log.warn(LogCategory.DISCOVERY, "Error determining whether %s is hidden: %s", path, e);
			return true;
		}
	}
}
