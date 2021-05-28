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

package net.fabricmc.loader.entrypoint;

import net.fabricmc.loader.launch.common.FabricLauncher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EntrypointTransformer {
	public static String appletMainClass;

	public final Logger logger = LogManager.getFormatterLogger("FabricLoader|EntrypointTransformer");
	private final List<EntrypointPatch> patches;
	private Map<String, byte[]> patchedClasses;
	private boolean entrypointsLocated = false;

	public EntrypointTransformer(Function<EntrypointTransformer, List<EntrypointPatch>> patches) {
		this.patches = patches.apply(this);
	}

	ClassNode loadClass(FabricLauncher launcher, String className) throws IOException {
		byte[] data = patchedClasses.containsKey(className) ? patchedClasses.get(className) : launcher.getClassByteArray(className, true);
		if (data != null) {
			ClassReader reader = new ClassReader(data);
			ClassNode node = new ClassNode();
			reader.accept(node, 0);
			return node;
		} else {
			return null;
		}
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

	public void locateEntrypoints(FabricLauncher launcher) {
		if (entrypointsLocated) {
			return;
		}

		entrypointsLocated = true;
		patchedClasses = new HashMap<>();

		patches.forEach((e) -> e.process(launcher, this::addPatchedClass));
		logger.debug("[EntrypointTransformer] Patched " + (patchedClasses.size() == 1 ? "1 class." : (patchedClasses.size() + " classes.")));
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
