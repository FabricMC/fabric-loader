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

package net.fabricmc.loader.transformer.accesswidener;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;

public class AccessWidenerRemapper {
	private final AccessWidener input;
	private final String to;
	private final Remapper remapper;

	public AccessWidenerRemapper(AccessWidener input, Remapper remapper, String to) {
		this.input = input;
		this.to = to;
		this.remapper = remapper;
	}

	public AccessWidener remap() {
		//Dont remap if we dont need to
		if (input.namespace.equals(to)) {
			return input;
		}

		AccessWidener remapped = new AccessWidener(FabricLoader.INSTANCE);
		remapped.namespace = to;

		for (Map.Entry<String, AccessWidener.Access> entry : input.classAccess.entrySet()) {
			remapped.classAccess.put(remapper.map(entry.getKey()), entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessWidener.Access> entry : input.methodAccess.entrySet()) {
			remapped.addOrMerge(remapped.methodAccess, remapMethod(entry.getKey()), entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessWidener.Access> entry : input.fieldAccess.entrySet()) {
			remapped.addOrMerge(remapped.fieldAccess, remapField(entry.getKey()), entry.getValue());
		}

		return remapped;
	}

	private EntryTriple remapMethod(EntryTriple entryTriple) {
		return new EntryTriple(
					remapper.map(entryTriple.getName()),
					remapper.mapMethodName(entryTriple.getOwner(), entryTriple.getName(), entryTriple.getDesc()),
					remapper.mapDesc(entryTriple.getDesc())
				);
	}

	private EntryTriple remapField(EntryTriple entryTriple) {
		return new EntryTriple(
				remapper.map(entryTriple.getName()),
				remapper.mapFieldName(entryTriple.getOwner(), entryTriple.getName(), entryTriple.getDesc()),
				remapper.mapDesc(entryTriple.getDesc())
		);
	}
}
