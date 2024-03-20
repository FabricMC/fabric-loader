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

import java.io.IOException;
import java.util.Objects;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() { }

	private static IMappingProvider.Member memberOf(String className, String memberName, String descriptor) {
		return new IMappingProvider.Member(className, memberName, descriptor);
	}

	public static IMappingProvider create(MappingTree mappings, String from, String to) {
		if (!Objects.equals(mappings.getSrcNamespace(), from)) {
			MemoryMappingTree filteredTree = new MemoryMappingTree();
			MappingSourceNsSwitch sourceSwitcher = new MappingSourceNsSwitch(filteredTree, from, true);

			try {
				mappings.accept(sourceSwitcher);

				mappings = filteredTree;
			} catch (IOException exception) {
				Log.warn(LogCategory.GAME_REMAP, "Failed to switch mappings source namespace...", exception);
			}
		}

		MappingTree finalMappings = mappings;

		return (acceptor) -> {
			final int fromId = finalMappings.getNamespaceId(from);
			final int toId = finalMappings.getNamespaceId(to);

			for (MappingTree.ClassMapping classDef : finalMappings.getClasses()) {
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
