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

package net.fabricmc.test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import net.fabricmc.loader.impl.lib.gson.JsonReader;

public final class McVersionLookupTest {
	public static void main(String[] args) throws IOException {
		if (args.length != 1) throw new RuntimeException("usage: <file/dir-to-try>");

		Path path = Paths.get(args[0]);
		List<String> invalid = new ArrayList<>();

		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".jar")) {
						check(file, path.relativize(file).toString(), invalid);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			check(path, path.getFileName().toString(), invalid);
		}

		System.out.println();

		if (invalid.isEmpty()) {
			System.out.println("All passed!");
		} else {
			System.out.println("Invalid:");

			for (String s : invalid) {
				System.out.println(s);
			}

			System.out.printf("%d invalid results%n", invalid.size());
		}
	}

	private static void check(Path file, String name, List<String> invalid) {
		String jsonId = null;
		String jsonName = null;

		try (ZipFile zf = new ZipFile(file.toFile())) {
			ZipEntry entry = zf.getEntry("version.json");

			if (entry != null) {
				try (JsonReader reader = new JsonReader(new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8))) {
					reader.beginObject();

					while (reader.hasNext()) {
						switch (reader.nextName()) {
						case "id":
							jsonId = reader.nextString();
							break;
						case "name":
							jsonName = reader.nextString();
							break;
						default:
							reader.skipValue();
						}
					}

					reader.endObject();
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		McVersion result = McVersionLookup.getVersionExceptClassVersion(file);
		String msg = String.format("%s: %s (raw=%s id=%s name=%s)", name, result.getNormalized(), result.getRaw(), jsonId, jsonName);
		System.out.println(msg);

		if (!pattern.matcher(result.getNormalized()).matches()) {
			System.out.println("** invalid!");
			invalid.add(msg);
		}
	}

	private static final Pattern pattern = Pattern.compile(
			"(0|[1-9]\\d*)" // major
			+ "\\.(0|[1-9]\\d*)" // minor
			+ "(\\.(0|[1-9]\\d*))?" // patch
			+ "(-(alpha|beta|rc)" // pre-release name (alpha = old mc alpha or snapshot, beta = old mc beta, rc = mc pre-release)
			+ "\\.(0|[1-9]\\d*)" // alpha major or pre-release major
			+ "(\\.(0|[1-9]\\d*))?" // alpha minor or pre-release minor
			+ "(\\.([1-9]\\d*|[a-z]))?" // alpha patch or pre-release suffix
			+ ")?");
}
