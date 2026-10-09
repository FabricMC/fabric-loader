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

package net.fabricmc.loader.impl.game.minecraft.menu;

import org.objectweb.asm.Type;

import net.fabricmc.loader.api.MappingResolver;

public final class MenuMappings {
	private final MappingResolver resolver;

	public MenuMappings(MappingResolver resolver) {
		this.resolver = resolver != null && resolver.getNamespaces().contains("intermediary") ? resolver : null;
	}

	public String className(String name) {
		return resolver == null ? name : resolver.mapClassName("intermediary", name.replace('/', '.')).replace('.', '/');
	}

	public boolean method(String owner, String descriptor, String name, String... alternatives) {
		for (String candidate : alternatives) {
			if (name.equals(candidate)) return true;

			if (resolver != null && candidate.startsWith("method_")) {
				String intermediaryOwner = resolver.unmapClassName("intermediary", owner.replace('/', '.'));
				if (name.equals(resolver.mapMethodName("intermediary", intermediaryOwner, candidate, descriptor(descriptor)))) return true;
			}
		}

		return false;
	}

	public boolean field(String owner, String descriptor, String name, String... alternatives) {
		for (String candidate : alternatives) {
			if (name.equals(candidate)) return true;

			if (resolver != null && candidate.startsWith("field_")) {
				String intermediaryOwner = resolver.unmapClassName("intermediary", owner.replace('/', '.'));
				if (name.equals(resolver.mapFieldName("intermediary", intermediaryOwner, candidate, type(Type.getType(descriptor)).getDescriptor()))) return true;
			}
		}

		return false;
	}

	private String descriptor(String descriptor) {
		Type[] arguments = Type.getArgumentTypes(descriptor);

		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = type(arguments[i]);
		}

		return Type.getMethodDescriptor(type(Type.getReturnType(descriptor)), arguments);
	}

	private Type type(Type type) {
		if (type.getSort() == Type.OBJECT) {
			return Type.getObjectType(resolver.unmapClassName("intermediary", type.getClassName()).replace('.', '/'));
		} else if (type.getSort() == Type.ARRAY) {
			StringBuilder descriptor = new StringBuilder();

			for (int i = 0; i < type.getDimensions(); i++) {
				descriptor.append('[');
			}

			return Type.getType(descriptor.append(type(type.getElementType()).getDescriptor()).toString());
		}

		return type;
	}
}
