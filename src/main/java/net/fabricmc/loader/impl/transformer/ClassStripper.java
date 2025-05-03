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

package net.fabricmc.loader.impl.transformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Strips the specified interfaces, fields and methods from a class.
 */
public class ClassStripper extends ClassVisitor {
	private final Collection<String> stripInterfaces;
	private final Collection<String> stripFields;
	private final Collection<String> stripMethods;
	private String className;

	public ClassStripper(int api, ClassVisitor classVisitor, Collection<String> stripInterfaces, Collection<String> stripFields, Collection<String> stripMethods) {
		super(api, classVisitor);
		this.stripInterfaces = stripInterfaces;
		this.stripFields = stripFields;
		this.stripMethods = stripMethods;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.className = name;

		if (!this.stripInterfaces.isEmpty()) {
			List<String> interfacesList = new ArrayList<>();

			for (String itf : interfaces) {
				if (!this.stripInterfaces.contains(itf)) {
					interfacesList.add(itf);
				}
			}

			interfaces = interfacesList.toArray(new String[0]);
		}

		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		if (stripFields.contains(name + descriptor)) {
			return null;
		} else {
			return super.visitField(access, name, descriptor, signature, value);
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (stripMethods.contains(name + descriptor)) {
			return null;
		}

		MethodVisitor ret = super.visitMethod(access, name, descriptor, signature, exceptions);

		// strip field writes from static init and constructor
		if (!stripFields.isEmpty() && (name.equals("<clinit>") || name.equals("<init>"))) {
			ret = new MethodVisitor(api, ret) {
				@Override
				public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
					if (opcode != Opcodes.PUTFIELD && opcode != Opcodes.PUTSTATIC // ignore non-writes
							|| !owner.equals(className) // ignore writes to other classes
							|| !stripFields.contains(name+descriptor)) { // ignore non-stripped fields
						super.visitFieldInsn(opcode, owner, name, descriptor);
					} else {
						if (opcode == Opcodes.PUTFIELD) {
							super.visitInsn(Opcodes.POP); // pop this
						}

						super.visitInsn(descriptor.equals("J") || descriptor.equals("D") ? Opcodes.POP2 : Opcodes.POP); // pop field value
					}
				}
			};
		}

		return ret;
	}
}
