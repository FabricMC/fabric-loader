package net.fabricmc.loader.transformer.decapsulator;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.mappings.EntryTriple;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Decapsulator {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> methodAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> fieldAccess = new HashMap<>();
	private Set<String> classes = new LinkedHashSet<>();

	public void loadFromMods(FabricLoader fabricLoader){
		for (ModContainer modContainer : fabricLoader.getAllMods()) {
			LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
			String decapsulator = modMetadata.getDecapsulator();

			if (decapsulator != null) {
				Path path = modContainer.getPath(decapsulator);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					read(reader, fabricLoader.getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read decapsulator file from mod " + modMetadata.getId(), e);
				}
			}
		}
	}

	public void read(BufferedReader reader, String currentNamespace) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (header.length != 2 || !header[0].equals("decapsulator\\v1")) {
			throw new UnsupportedOperationException("Unsupported or invalid access decapsulator file, expected: decapsulator\\v1 <namespace>");
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
				throw new RuntimeException("Decapsulator contains one or more space character, tabs are required on line: " + line);
			}

			String[] split = line.split("\t");

			if (split.length != 3 && split.length != 5) {
				throw new RuntimeException(String.format("Invalid line (%s)", line));
			}

			Access access = parseAccess(split[0]);

			targets.add(split[2].replaceAll("/", "."));

			switch (split[1]) {
				case "class":
					if (split.length != 3) {
						throw new RuntimeException(String.format("Expected (<access>\tclass\t<className>) got (%s)", line));
					}

					if (classAccess.containsKey(split[2])) {
						classAccess.put(split[2], mergeAccess(access, classAccess.get(split[2])));
					} else {
						classAccess.put(split[2], access);
					}

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

	void addOrMerge(Map<EntryTriple, ChangeList> map, EntryTriple entry, Access access) {
		if (entry == null || access == null) {
			throw new RuntimeException("Input entry or access is null");
		}

		if (map.containsKey(entry)) {
			map.get(entry).add(access);
		} else {
			map.put(entry, new ChangeList(access));
		}
	}

	private Access parseAccess(String input) {
		switch (input.toLowerCase(Locale.ROOT)) {
			case "public":
				return Access.PUBLIC;
			case "protected":
				return Access.PROTECTED;
			case "stripfinal":
				return Access.STRIP_FINAL;
			default:
				throw new UnsupportedOperationException("Unknown access type:" + input);
		}
	}

	private static Access mergeAccess(Access a, Access b) {
		return Access.values()[Math.max(a.ordinal(), b.ordinal())];
	}

	public Access getClassAccess(String className) {
		return classAccess.getOrDefault(className, Access.DEFAULT);
	}

	public ChangeList getFieldAccess(EntryTriple entryTriple) {
		return fieldAccess.getOrDefault(entryTriple, ChangeList.EMPTY);
	}

	public ChangeList getMethodAccess(EntryTriple entryTriple) {
		return methodAccess.getOrDefault(entryTriple, ChangeList.EMPTY);
	}

	public Set<String> getTargets() {
		return classes;
	}

	public enum Access {
		DEFAULT,
		PROTECTED,
		PUBLIC,

		STRIP_FINAL;

		public int apply(int access) {
			switch (this) {
				case DEFAULT:
					return access;
				case PUBLIC:
					return (access & ~7) | Opcodes.ACC_PUBLIC;
				case PROTECTED:
					if ((access & Opcodes.ACC_PUBLIC) != 0) { //Already public
						return access;
					}

					return (access & ~7) | Opcodes.ACC_PROTECTED;
				case STRIP_FINAL:
					return access & ~Opcodes.ACC_FINAL; //Remove final
				default:
					throw new RuntimeException("Something bad happened");
			}
		}
	}

	public static class ChangeList {
		public static final ChangeList EMPTY = new ChangeList();

		private final List<Access> changes = new ArrayList<>();

		public ChangeList() {
		}

		public ChangeList(Access access) {
			this();
			add(access);
		}

		public void add(Access access) {
			if (changes.contains(access)) {
				return;
			}

			if (access == Access.PUBLIC) {
				changes.remove(Access.PROTECTED);
			}

			changes.add(access);
		}

		public int apply(int access) {
			for (Access change : getChanges()) {
				access = change.apply(access);
			}

			return access;
		}

		public List<Access> getChanges() {
			return changes;
		}
	}

}
