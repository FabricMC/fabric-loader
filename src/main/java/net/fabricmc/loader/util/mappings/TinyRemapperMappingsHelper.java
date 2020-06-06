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

package net.fabricmc.loader.util.mappings;

import com.google.common.base.Strings;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.Mapped;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() {

	}

	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}

	public static String tryName(Mapped mapping, String namespace, String fallback) {
		String name = mapping.getRawName(namespace);
		return !Strings.isNullOrEmpty(name) ? name : fallback;
	}

	public static IMappingProvider create(TinyTree mappings, String from, String to) {
		return (acceptor) -> {
			for (ClassDef classDef : mappings.getClasses()) {
				String className = classDef.getRawName(from);
				if (Strings.isNullOrEmpty(className)) continue; //Class not present
				acceptor.acceptClass(className, tryName(classDef, to, className));

				for (FieldDef field : classDef.getFields()) {
					String fieldName = field.getRawName(from);
					if (Strings.isNullOrEmpty(fieldName)) continue; //Field not present
					acceptor.acceptField(memberOf(className, fieldName, field.getDescriptor(from)), tryName(field, to, fieldName));
				}

				for (MethodDef method : classDef.getMethods()) {
					String methodName = method.getRawName(from);
					if (Strings.isNullOrEmpty(methodName)) continue; //Method not present
					acceptor.acceptMethod(memberOf(className, methodName, method.getDescriptor(from)), tryName(method, to, methodName));
				}
			}
		};
	}
}
