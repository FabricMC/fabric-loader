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
import org.objectweb.asm.tree.*;

import java.io.File;

public class GameTransformer implements IClassTransformer {

	private static boolean FOUND_MINECRAFT_GAME = false;
	private static String MINECRAFT_GAME_CLASS = "";
	private static boolean FOUND_MINECRAFT_GAME_START = false;
	private static String MINECRAFT_GAME_START_NAME = "";
	private static String MINECRAFT_GAME_START_DESC = "";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (transformedName.equals("net.minecraft.client.main.Main")) {
			ClassNode classNode = ASMUtils.readClassFromBytes(basicClass);
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals("main")) {
					boolean foundClientThreadReferance = false;
					for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
						if (insnNode instanceof LdcInsnNode) {
							if (((LdcInsnNode) insnNode).cst.equals("Client thread")) {
								foundClientThreadReferance = true;
							}
						}
						if (foundClientThreadReferance && insnNode instanceof TypeInsnNode) {
							MINECRAFT_GAME_CLASS = ((TypeInsnNode) insnNode).desc.replace("/", ".");
							FOUND_MINECRAFT_GAME = true;
						}
						if (FOUND_MINECRAFT_GAME && insnNode instanceof MethodInsnNode) {
							if (!((MethodInsnNode) insnNode).name.equals("<init>") && !FOUND_MINECRAFT_GAME_START) {
								MINECRAFT_GAME_START_NAME = ((MethodInsnNode) insnNode).name;
								MINECRAFT_GAME_START_DESC = ((MethodInsnNode) insnNode).desc;
								FOUND_MINECRAFT_GAME_START = true;
							}
						}
					}
				}
			}
		} else if (transformedName.equals(MINECRAFT_GAME_CLASS)) {
			ClassNode classNode = ASMUtils.readClassFromBytes(basicClass);
			boolean foundInitMethod = false;
			String initMethodName = "";
			String initMethodDesc = "";
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals(MINECRAFT_GAME_START_NAME) && methodNode.desc.equals(MINECRAFT_GAME_START_DESC)) {
					for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
						if (insnNode instanceof MethodInsnNode) {
							if (insnNode.getOpcode() == Opcodes.INVOKESPECIAL && !foundInitMethod) {
								initMethodName = ((MethodInsnNode) insnNode).name;
								initMethodDesc = ((MethodInsnNode) insnNode).desc;
								foundInitMethod = true;
								break;
							}
						}
					}
				}
			}
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals(initMethodName) && methodNode.desc.equals(initMethodDesc)) {
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
