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

package net.fabricmc.loader.impl.util.mappings;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() { }

	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}

	public static IMappingProvider create(MappingTree mappings, String from, String to) {
		return (acceptor) -> {
			final int fromId = mappings.getNamespaceId(from);
			final int toId = mappings.getNamespaceId(to);

			for (MappingTree.ClassMapping classDef : mappings.getClasses()) {
				final String className = classDef.getName(fromId);
				String dstName = classDef.getName(toId);

				if (dstName == null) {
					dstName = className;
				}

				acceptor.acceptClass(className, dstName);

				for (MappingTree.FieldMapping field : classDef.getFields()) {
					acceptor.acceptField(memberOf(className, field.getName(fromId), field.getDesc(fromId)), field.getName(toId));
				}

				for (MappingTree.MethodMapping method : classDef.getMethods()) {
					IMappingProvider.Member methodIdentifier = memberOf(className, method.getName(fromId), method.getDesc(fromId));
					acceptor.acceptMethod(methodIdentifier, method.getName(toId));
				}
			}
		};
	}
}
