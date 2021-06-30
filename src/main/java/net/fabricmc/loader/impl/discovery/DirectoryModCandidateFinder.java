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

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class DirectoryModCandidateFinder implements ModCandidateFinder {
	private final Path path;
	private final boolean requiresRemap;

	public DirectoryModCandidateFinder(Path path, boolean requiresRemap) {
		this.path = path;
		this.requiresRemap = requiresRemap;
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
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
			Files.walkFileTree(this.path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					/*
					 * We only propose a file as a possible mod in the following scenarios:
					 * General: Must be a jar file
					 *
					 * Some OSes Generate metadata so consider the following because of OSes:
					 * UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`
					 * MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`
					 */

					String fileName = file.getFileName().toString();

					if (fileName.endsWith(".jar") && !fileName.startsWith(".") && !Files.isHidden(file)) {
						out.accept(file, requiresRemap);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Exception while searching for mods in '" + path + "'!", e);
		}
	}
}
