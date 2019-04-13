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

import net.fabricmc.mappings.*;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.MemberInstance;

public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() {

	}

	public static IMappingProvider create(Mappings mappings, String from, String to) {
		return (classMap, fieldMap, methodMap) -> {
			for (ClassEntry entry : mappings.getClassEntries()) {
				classMap.put(entry.get(from), entry.get(to));
			}

			for (FieldEntry entry : mappings.getFieldEntries()) {
				EntryTriple fromTriple = entry.get(from);
				fieldMap.put(fromTriple.getOwner() + "/" + MemberInstance.getFieldId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
			}

			for (MethodEntry entry : mappings.getMethodEntries()) {
				EntryTriple fromTriple = entry.get(from);
				methodMap.put(fromTriple.getOwner() + "/" + MemberInstance.getMethodId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
			}
		};
	}
}
