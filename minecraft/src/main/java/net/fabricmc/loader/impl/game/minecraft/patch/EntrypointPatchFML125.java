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

package net.fabricmc.loader.impl.game.minecraft.patch;

import java.io.IOException;
import java.util.function.Consumer;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class EntrypointPatchFML125 extends GamePatch {
	private static final String FROM = ModClassLoader_125_FML.class.getName();
	private static final String TO = "cpw.mods.fml.common.ModClassLoader";
	private static final String FROM_INTERNAL = FROM.replace('.', '/');
	private static final String TO_INTERNAL = "cpw/mods/fml/common/ModClassLoader";

	public EntrypointPatchFML125(GameTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		if (classExists(launcher, TO)
				&& !classExists(launcher, "cpw.mods.fml.relauncher.FMLRelauncher")) {
			if (!(launcher instanceof Knot)) {
				throw new RuntimeException("1.2.5 FML patch only supported on Knot!");
			}

			Log.debug(LogCategory.GAME_PATCH, "Detected 1.2.5 FML - Knotifying ModClassLoader...");

			try {
				ClassNode patchedClassLoader = loadClass(launcher, FROM);
				ClassNode remappedClassLoader = new ClassNode();

				patchedClassLoader.accept(new ClassRemapper(remappedClassLoader, new Remapper() {
					@Override
					public String map(String internalName) {
						return FROM_INTERNAL.equals(internalName) ? TO_INTERNAL : internalName;
					}
				}));

				classEmitter.accept(remappedClassLoader);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
