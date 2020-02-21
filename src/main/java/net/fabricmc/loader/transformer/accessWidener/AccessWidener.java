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

package net.fabricmc.loader.transformer.accessWidener;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AccessWidener {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, Access> methodAccess = new HashMap<>();
	public Map<EntryTriple, Access> fieldAccess = new HashMap<>();
	private Set<String> classes = new LinkedHashSet<>();

	public void loadFromMods(FabricLoader fabricLoader){
		for (ModContainer modContainer : fabricLoader.getAllMods()) {
			LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
			String accessWidener = modMetadata.getAccessWidener();

			if (accessWidener != null) {
				Path path = modContainer.getPath(accessWidener);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					read(reader, fabricLoader.getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read accessWidener file from mod " + modMetadata.getId(), e);
				}
			}
		}
	}

	public void read(BufferedReader reader, String currentNamespace) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (header.length != 2 || !header[0].equals("accessWidener\\v1")) {
			throw new UnsupportedOperationException("Unsupported or invalid access accessWidener file, expected: accessWidener\\v1 <namespace>");
		}

		if (!header[1].equals(currentNamespace)) {
			throw new RuntimeException(String.format("Namespace (%s) does not match current runtime namespace (%s)", header[1], currentNamespace));
		}

		if (namespace != null) {
			if (!namespace.equals(header[1])) {
				throw new RuntimeException(String.format("Namespace mismatch, expected %s got %s", namespace, header[1]));
			}
		}

		namespace = header[1];

		String line;

		Set<String> targets = new LinkedHashSet<>();

		while ((line = reader.readLine()) != null) {
			//Comment handling
			int commentPos = line.indexOf('#');

			if (commentPos >= 0) {
				line = line.substring(0, commentPos).trim();
			}

			if (line.isEmpty()) continue;

			//Will be a common issue, make it clear.
			if (line.contains(" ")) {
				throw new RuntimeException("AccessWidener contains one or more space character, tabs are required on line: " + line);
			}

			String[] split = line.split("\t");

			if (split.length != 3 && split.length != 5) {
				throw new RuntimeException(String.format("Invalid line (%s)", line));
			}

			String access = split[0];

			targets.add(split[2].replaceAll("/", "."));

			switch (split[1]) {
				case "class":
					if (split.length != 3) {
						throw new RuntimeException(String.format("Expected (<access>\tclass\t<className>) got (%s)", line));
					}

					classAccess.put(split[2], applyAccess(access, classAccess.getOrDefault(split[2], Access.DEFAULT)));
					break;
				case "field":
					if (split.length != 5) {
						throw new RuntimeException(String.format("Expected (<access>\tfield\t<className>\t<fieldName>\t<fieldDesc>) got (%s)", line));
					}

					addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access);
					break;
				case "method":
					if (split.length != 5) {
						throw new RuntimeException(String.format("Expected (<access>\tmethod\t<className>\t<methodName>\t<methodDesc>) got (%s)", line));
					}

					addOrMerge(methodAccess, new EntryTriple(split[2], split[3], split[4]), access);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported type " + split[1]);
			}
		}

		Set<String> parentClasses = new LinkedHashSet<>();

		//Also transform all parent classes
		for (String clazz : targets) {
			while (clazz.contains("$")) {
				clazz = clazz.substring(0, clazz.lastIndexOf("$"));
				parentClasses.add(clazz);
			}
		}

		classes.addAll(targets);
		classes.addAll(parentClasses);
	}

	void addOrMerge(Map<EntryTriple, Access> map, EntryTriple entry, String access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		map.put(entry, applyAccess(access, map.getOrDefault(entry, Access.DEFAULT)));
	}

	private Access applyAccess(String input, Access access) {
		switch (input.toLowerCase(Locale.ROOT)) {
			case "public":
				return access.makePublic();
			case "protected":
				return access.makeProtected();
			case "stripfinal":
				return access.stripFinal();
			default:
				throw new UnsupportedOperationException("Unknown access type:" + input);
		}
	}

	public Access getClassAccess(String className) {
		return classAccess.getOrDefault(className, Access.DEFAULT);
	}

	public Access getFieldAccess(EntryTriple entryTriple) {
		return fieldAccess.getOrDefault(entryTriple, Access.DEFAULT);
	}

	public Access getMethodAccess(EntryTriple entryTriple) {
		return methodAccess.getOrDefault(entryTriple, Access.DEFAULT);
	}

	public Set<String> getTargets() {
		return classes;
	}

	public enum Access {
		DEFAULT(false, false, false),
		PROTECTED(true, false, false),
		PROTECTED_STRIP_FINAL(true,false, true),
		PUBLIC(false, true, false),
		PUBLIC_STRIP_FINAL(false,true, true),
		STRIP_FINAL(false, false, true);

		private final boolean makeProtected;
		private final boolean makePublic;
		private final boolean stripFinal;

		Access(boolean makeProtected, boolean makePublic, boolean stripFinal) {
			this.makeProtected = makeProtected;
			this.makePublic = makePublic;
			this.stripFinal = stripFinal;
		}

		public Access makePublic() {
			return stripFinal ? PUBLIC_STRIP_FINAL : PUBLIC;
		}

		public Access makeProtected() {
			if (makePublic) return this;
			return stripFinal ? PROTECTED_STRIP_FINAL : PROTECTED;
		}

		public Access stripFinal() {
			if (makePublic) {
				return PUBLIC_STRIP_FINAL;
			} else if (makeProtected) {
				return PROTECTED_STRIP_FINAL;
			}
			return STRIP_FINAL;
		}

		public int apply(int access) {
			if (makePublic) {
				access = (access & ~7) | Opcodes.ACC_PUBLIC;
			} else if (makeProtected) {
				if ((access & Opcodes.ACC_PUBLIC) == 0) {
					//Only make it protected if not public
					access = (access & ~7) | Opcodes.ACC_PROTECTED;
				}
			}

			if (stripFinal) {
				access = access & ~Opcodes.ACC_FINAL;;
			}

			return access;
		}
	}
}
