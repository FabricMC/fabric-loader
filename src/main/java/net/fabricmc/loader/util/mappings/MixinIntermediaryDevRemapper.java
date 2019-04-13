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
import net.fabricmc.mappings.helpers.mixin.MixinMappingsRemapper;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.util.*;

public class MixinIntermediaryDevRemapper extends MixinMappingsRemapper {
	private final Set<String> allPossibleClassNames;
	private final Map<String, Set<String>> nameDescFieldLookup, nameDescMethodLookup;

	private static String getNameDescKey(EntryTriple triple) {
		return triple.getName() + ";;" + triple.getDesc();
	}

	public MixinIntermediaryDevRemapper(Mappings mappings, String from, String to) {
		super(mappings, from, to);

		// I sincerely hate that I have to do this.

		nameDescFieldLookup = new HashMap<>();
		nameDescMethodLookup = new HashMap<>();
		allPossibleClassNames = new HashSet<>();

		for (FieldEntry entry : mappings.getFieldEntries()) {
			EntryTriple tripleFrom = entry.get(from);
			EntryTriple tripleTo = entry.get(to);

			nameDescFieldLookup.computeIfAbsent(getNameDescKey(tripleFrom), (a) -> new HashSet<>()).add(tripleTo.getName());
		}

		for (MethodEntry entry : mappings.getMethodEntries()) {
			EntryTriple tripleFrom = entry.get(from);
			EntryTriple tripleTo = entry.get(to);

			nameDescMethodLookup.computeIfAbsent(getNameDescKey(tripleFrom), (a) -> new HashSet<>()).add(tripleTo.getName());
		}

		for (ClassEntry entry : mappings.getClassEntries()) {
			allPossibleClassNames.add(entry.get(from));
			allPossibleClassNames.add(entry.get(to));
		}
	}

	private void throwAmbiguousLookup(String type, String name, String desc, Set<String> values) {
		StringBuilder builder = new StringBuilder("Ambiguous Mixin " + type + " lookup: " + name + " " + desc + " -> ");
		int i = 0;
		for (String s : values) {
			if ((i++) > 0) {
				builder.append(", ");
			}

			builder.append(s);
		}
		throw new RuntimeException(builder.toString());
	}

	private String mapMethodNameInner(String owner, String name, String desc) {
		String result = super.mapMethodName(owner, name, desc);
		if (result.equals(name)) {
			String otherClass = unmap(owner);
			return super.mapMethodName(otherClass, name, unmapDesc(desc));
		} else {
			return result;
		}
	}

	private String mapFieldNameInner(String owner, String name, String desc) {
		String result = super.mapFieldName(owner, name, desc);
		if (result.equals(name)) {
			String otherClass = unmap(owner);
			return super.mapFieldName(otherClass, name, unmapDesc(desc));
		} else {
			return result;
		}
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		// handle unambiguous values early
		if (owner == null || allPossibleClassNames.contains(owner)) {
			Set<String> values = nameDescMethodLookup.get(name + ";;" + desc);
			if (values != null && !values.isEmpty()) {
				if (values.size() > 1) {
					if (owner == null) {
						throwAmbiguousLookup("method", name, desc, values);
					}
				} else {
					return values.iterator().next();
				}
			} else if (owner == null) {
				return name;
			} else {
				// TODO: this should not repeat more than once
				String unmapOwner = unmap(owner);
				String unmapDesc = unmapDesc(desc);
				if (!unmapOwner.equals(owner) || !unmapDesc.equals(desc)) {
					return mapMethodName(unmapOwner, name, unmapDesc);
				} else {
					// take advantage of the fact allPossibleClassNames
					// and nameDescLookup cover all sets; if none are present,
					// we don't have a mapping for it.
					return name;
				}
			}
		}

		LinkedList<ClassInfo> classInfos = new LinkedList<ClassInfo>();
		classInfos.add(ClassInfo.forName(owner));

		while (!classInfos.isEmpty()) {
			ClassInfo c = classInfos.remove();
			String ownerO = unmap(c.getName());
			String s;
			if (!(s = mapMethodNameInner(ownerO, name, desc)).equals(name)) {
				return s;
			}

			if (!c.getSuperName().startsWith("java/")) {
				ClassInfo cSuper = c.getSuperClass();
				if (cSuper != null) {
					classInfos.add(cSuper);
				}
			}

			for (String itf : c.getInterfaces()) {
				if (itf.startsWith("java/")) {
					continue;
				}

				ClassInfo cItf = ClassInfo.forName(itf);
				if (cItf != null) {
					classInfos.add(cItf);
				}
			}
		}

		return name;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		// handle unambiguous values early
		if (owner == null || allPossibleClassNames.contains(owner)) {
			Set<String> values = nameDescFieldLookup.get(name + ";;" + desc);
			if (values != null && !values.isEmpty()) {
				if (values.size() > 1) {
					if (owner == null) {
						throwAmbiguousLookup("field", name, desc, values);
					}
				} else {
					return values.iterator().next();
				}
			} else if (owner == null) {
				return name;
			} else {
				// TODO: this should not repeat more than once
				String unmapOwner = unmap(owner);
				String unmapDesc = unmapDesc(desc);
				if (!unmapOwner.equals(owner) || !unmapDesc.equals(desc)) {
					return mapFieldName(unmapOwner, name, unmapDesc);
				} else {
					// take advantage of the fact allPossibleClassNames
					// and nameDescLookup cover all sets; if none are present,
					// we don't have a mapping for it.
					return name;
				}
			}
		}

		ClassInfo c = ClassInfo.forName(map(owner));

		while (c != null) {
			String nextOwner = unmap(c.getName());
			String s;
			if (!(s = mapFieldNameInner(nextOwner, name, desc)).equals(name)) {
				return s;
			}

			if (c.getSuperName().startsWith("java/")) {
				break;
			}

			c = c.getSuperClass();
		}

		return name;
	}
}
