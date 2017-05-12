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

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class BrandTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (transformedName.equals("net.minecraft.client.ClientBrandRetriever")) {
			ClassNode classNode = ASMUtils.readClassFromBytes(basicClass);
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals("getClientModName")) {
					for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
						if (insnNode instanceof LdcInsnNode) {
							((LdcInsnNode) insnNode).cst = "Fabric";
						}
					}
				}
			}
			return ASMUtils.writeClassToBytes(classNode);
		} else if (transformedName.equals("net.minecraft.client.MixinMinecraftServer")) {
			ClassNode classNode = ASMUtils.readClassFromBytes(basicClass);
			for (MethodNode methodNode : classNode.methods) {
				if (methodNode.name.equals("getServerModName")) {
					for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
						if (insnNode instanceof LdcInsnNode) {
							((LdcInsnNode) insnNode).cst = "Fabric";
						}
					}
				}
			}
			return ASMUtils.writeClassToBytes(classNode);
		}
		return basicClass;
	}
}
