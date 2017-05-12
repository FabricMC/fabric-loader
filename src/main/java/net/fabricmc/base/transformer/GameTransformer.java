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

package net.fabricmc.base.transformer;

import net.fabricmc.base.Fabric;
import net.fabricmc.base.client.ClientSidedHandler;
import net.fabricmc.base.loader.Loader;
import net.minecraft.client.MinecraftGame;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;

public class GameTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (transformedName.equals("net.minecraft.client.MinecraftGame")) {
			ClassNode classNode = ASMUtils.readClassFromBytes(basicClass);
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals("init")) {
					methodNode.instructions.insertBefore(methodNode.instructions.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/base/transformer/GameTransformer", "init", "()V", false));
				} else if (methodNode.name.equals("createDisplay")) { //TODO move out of the loader and into base
					methodNode.instructions.insertBefore(methodNode.instructions.get(0), new InsnNode(Opcodes.RETURN));
				} else if (methodNode.name.equals("setDisplayMode")) {
					methodNode.instructions.insertBefore(methodNode.instructions.get(0), new InsnNode(Opcodes.RETURN));
				}
			}
			return ASMUtils.writeClassToBytes(classNode);
		}
		return basicClass;
	}

	public static void init() {
		Fabric.initialize(MinecraftGame.getInstance().runDirectory, new ClientSidedHandler());
		Loader.INSTANCE.load(new File(MinecraftGame.getInstance().runDirectory, "mods"));
	}

}
