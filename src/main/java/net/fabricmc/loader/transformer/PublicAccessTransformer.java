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

package net.fabricmc.loader.transformer;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class PublicAccessTransformer implements IClassTransformer {
	static class AccessClassVisitor extends ClassVisitor {
		public AccessClassVisitor(int api, ClassVisitor classVisitor) {
			super(api, classVisitor);
		}

		@Override
		public void visit(
			final int version,
			final int access,
			final String name,
			final String signature,
			final String superName,
			final String[] interfaces) {
			super.visit(version, (access & (~0x7)) | Opcodes.ACC_PUBLIC, name, signature, superName, interfaces);
		}

		@Override
		public void visitInnerClass(
			final String name, final String outerName, final String innerName, final int access) {
			super.visitInnerClass(name, outerName, innerName, (access & (~0x7)) | Opcodes.ACC_PUBLIC);
		}

		@Override
		public FieldVisitor visitField(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final Object value) {
			return super.visitField((access & (~0x7)) | Opcodes.ACC_PUBLIC, name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions) {
			return super.visitMethod((access & (~0x7)) | Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions);
		}
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (!name.startsWith("net.minecraft") && name.contains(".")) {
			return bytes;
		}
		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = new ClassWriter(0);
		classReader.accept(new AccessClassVisitor(Opcodes.ASM6, classWriter), 0);
		return classWriter.toByteArray();
	}
}
