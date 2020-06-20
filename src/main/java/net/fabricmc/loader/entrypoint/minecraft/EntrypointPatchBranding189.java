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

package net.fabricmc.loader.entrypoint.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchBranding189 extends EntrypointPatch {

	public EntrypointPatchBranding189(EntrypointTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT){
			loadClass(launcher, "net/minecraft/client/MinecraftClient").methods.forEach(m -> {
				String titleScreen = null;

				ListIterator<AbstractInsnNode> instructions = m.instructions.iterator();

				while(instructions.hasNext()) {
					AbstractInsnNode node = instructions.next();

					if (node instanceof LdcInsnNode && "Post startup".equals(((LdcInsnNode) node).cst)) {
						while (instructions.hasNext()) {
							node = instructions.next();

							if (node instanceof MethodInsnNode) {
								MethodInsnNode invoke = (MethodInsnNode) node;

								if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.name.equals("<init>") && invoke.desc.equals("()V")) {
									titleScreen = invoke.owner;
								}
							}
						}

						break;
					}
				}
				if(titleScreen == null) {
					return;
				}
				ClassNode titleScreenClass = loadClass(launcher, titleScreen);
				titleScreenClass.methods.forEach(m2 -> {
					ListIterator<AbstractInsnNode> instructions2 = m2.instructions.iterator();

					while (instructions2.hasNext()) {
						AbstractInsnNode node = instructions2.next();

						if (node instanceof LdcInsnNode) {
							String constant = String.valueOf(((LdcInsnNode) node).cst);

							if (constant.startsWith("Minecraft ")) {
								instructions2.set(new LdcInsnNode(constant + "/Fabric"));
							}
						}
					}
				});

				classEmitter.accept(titleScreenClass);
			});
		}
	}

	@Override
	protected ClassNode loadClass(FabricLauncher launcher, String className) {
		try {
			return super.loadClass(launcher, className);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
