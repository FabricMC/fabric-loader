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

package net.fabricmc.loader.util;

import net.fabricmc.mappings.*;
import net.fabricmc.tinyremapper.IMappingProvider;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() {

	}

	private static String entryToValueString(EntryTriple triple) {
		return triple.getOwner() + "/" + triple.getName();
	}

	private static String fieldToString(EntryTriple triple) {
		return triple.getOwner() + "/" + triple.getName() + ";;" + triple.getDesc();
	}

	private static String methodToString(EntryTriple triple) {
		return triple.getOwner() + "/" + triple.getName() + triple.getDesc();
	}

	public static IMappingProvider create(Mappings mappings, String from, String to) {
		return (classMap, fieldMap, methodMap) -> {
			for (ClassEntry entry : mappings.getClassEntries()) {
				classMap.put(entry.get(from), entry.get(to));
			}

			for (FieldEntry entry : mappings.getFieldEntries()) {
				fieldMap.put(fieldToString(entry.get(from)), entryToValueString(entry.get(to)));
			}

			for (MethodEntry entry : mappings.getMethodEntries()) {
				fieldMap.put(methodToString(entry.get(from)), entryToValueString(entry.get(to)));
			}
		};
	}
}
