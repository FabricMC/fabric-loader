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

package net.fabricmc.loader.impl.game.patch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipError;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SimpleClassPath;
import net.fabricmc.loader.impl.util.SimpleClassPath.CpEntry;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class GameTransformer {
	private final List<GamePatch> patches;
	private Map<String, byte[]> patchedClasses;
	private boolean entrypointsLocated = false;

	public GameTransformer(GamePatch... patches) {
		this.patches = Arrays.asList(patches);
	}

	private void addPatchedClass(ClassNode node) {
		String key = node.name.replace('/', '.');

		if (patchedClasses.containsKey(key)) {
			throw new RuntimeException("Duplicate addPatchedClasses call: " + key);
		}

		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		patchedClasses.put(key, writer.toByteArray());
	}

	public void locateEntrypoints(FabricLauncher launcher, List<Path> gameJars) {
		if (entrypointsLocated) {
			return;
		}

		patchedClasses = new HashMap<>();

		try (SimpleClassPath cp = new SimpleClassPath(gameJars)) {
			Function<String, ClassReader> classSource = name -> {
				byte[] data = patchedClasses.get(name);

				if (data != null) {
					return new ClassReader(data);
				}

				try {
					CpEntry entry = cp.getEntry(LoaderUtil.getClassFileName(name));
					if (entry == null) return null;

					try (InputStream is = entry.getInputStream()) {
						return new ClassReader(is);
					} catch (IOException | ZipError e) {
						throw new RuntimeException(String.format("error reading %s in %s: %s", name, LoaderUtil.normalizePath(entry.getOrigin()), e), e);
					}
				} catch (IOException e) {
					throw ExceptionUtil.wrap(e);
				}
			};

			for (GamePatch patch : patches) {
				patch.process(launcher, classSource, this::addPatchedClass);
			}
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
		}

		Log.debug(LogCategory.GAME_PATCH, "Patched %d class%s", patchedClasses.size(), patchedClasses.size() != 1 ? "s" : "");
		entrypointsLocated = true;
	}

	/**
	 * This must run first, contractually!
	 * @param className The class name,
	 * @return The transformed class data.
	 */
	public byte[] transform(String className) {
		return patchedClasses.get(className);
	}
}
