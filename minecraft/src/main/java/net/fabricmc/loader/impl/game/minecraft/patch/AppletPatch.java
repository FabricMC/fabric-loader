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

import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;

/**
 * Redirect the Minecraft applet to use our stub implementation of Applet.
 */
public class AppletPatch extends GamePatch {
	private static final String FROM_PACKAGE = "java/applet/";
	private static final String TO_PACKAGE = "net/fabricmc/loader/impl/game/minecraft/applet/stub/";

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
		for (String appletClassName: new String[]{
				"net/minecraft/client/MinecraftApplet",
				"com/mojang/minecraft/MinecraftApplet"
		}) {
			ClassNode appletClass = classSource.apply(appletClassName);

			if (appletClass != null) {
				if (applyAppletPatch(appletClass)) {
					classEmitter.accept(appletClass);
				}
			}
		}
	}

	private static String replaceWithStub(String name) {
		return name.replace(FROM_PACKAGE, TO_PACKAGE);
	}

	private boolean applyAppletPatch(ClassNode classNode) {
		classNode.superName = replaceWithStub(classNode.superName);

		for (MethodNode method : classNode.methods) {
			for (AbstractInsnNode instruction : method.instructions) {
				if (instruction instanceof MethodInsnNode) {
					MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
					methodInsnNode.owner = replaceWithStub(methodInsnNode.owner);
				}
			}
		}

		return true;
	}
}
