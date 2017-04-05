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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class AccessTransformer implements IClassTransformer {

	//Very basic make everything public access transformer, when we have a file format this will read from that and not do this.
	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (!name.startsWith("net.minecraft") && name.contains(".")) {
			return bytes;
		}
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		boolean isClassProtected = classNode.access == Opcodes.ACC_PROTECTED;
		boolean isClassPrivate = classNode.access == Opcodes.ACC_PRIVATE;
		if (isClassProtected || isClassPrivate) {
			classNode.access = Opcodes.ACC_PUBLIC;
		}
		for (MethodNode method : classNode.methods) {
			boolean isProtected = method.access == Opcodes.ACC_PROTECTED;
			boolean isPrivate = method.access  == Opcodes.ACC_PRIVATE;
			boolean isDefault = method.access  == 0;
			//TODO remove this, or fix it
			if(name.equals("net.minecraft.world.gen.structure.StructureRegistry") && method.name.equals("registerPiece")){
				method.access = Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC;
			}
			if (isProtected || isPrivate || isDefault) {
				method.access = Opcodes.ACC_PUBLIC;
			}
		}
		for (FieldNode field : classNode.fields) {
			boolean isProtected = field.access == Opcodes.ACC_PROTECTED;
			boolean isPrivate = field.access == Opcodes.ACC_PRIVATE;
			if (isProtected || isPrivate) {
				field.access = Opcodes.ACC_PUBLIC;
			}
		}
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(writer);
		return writer.toByteArray();
	}
}
