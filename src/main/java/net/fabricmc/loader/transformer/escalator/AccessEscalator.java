package net.fabricmc.loader.transformer.escalator;

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

public class AccessEscalator {
	public String namespace;
	public Map<String, Access> classAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> methodAccess = new HashMap<>();
	public Map<EntryTriple, ChangeList> fieldAccess = new HashMap<>();
	private Set<String> classes = new LinkedHashSet<>();

	public void loadFromMods(FabricLoader fabricLoader){
		for (ModContainer modContainer : fabricLoader.getAllMods()) {
			LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
			String accessEscalator = modMetadata.getAccessEscalator();

			if (accessEscalator != null) {
				Path path = modContainer.getPath(accessEscalator);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					read(reader, fabricLoader.getMappingResolver().getCurrentRuntimeNamespace());
				} catch (IOException e) {
					throw new RuntimeException("Failed to read access escalator from mod " + modMetadata.getId(), e);
				}
			}
		}
	}

	public void read(BufferedReader reader, String currentNamespace) throws IOException {
		String[] header = reader.readLine().split("\t");

		if (header.length != 2 || !header[0].equals("ae\\v1")) {
			throw new UnsupportedOperationException("Unsupported or invalid access escalator file");
		}

		if (!header[1].equals(currentNamespace)) {
			throw new RuntimeException("Namespace does not match current runtime namespace");
		}

		if (namespace != null) {
			if (!namespace.equals(header[1])) {
				throw new RuntimeException("Namespace mismatch");
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
				throw new RuntimeException("Access escalator contains spaces, tabs are required on line: " + line);
			}

			String[] split = line.split("\t");

			if (split.length != 3 && split.length != 5) {
				throw new RuntimeException("Failed to parse access escalator. at line:" + line);
			}

			Access access = parseAccess(split[0]);

			targets.add(split[2].replaceAll("/", "."));

			switch (split[1]) {
				case "class":
					if (split.length != 3) {
						throw new RuntimeException("Failed to parse access escalator. at line:" + line);
					}

					if (classAccess.containsKey(split[2])) {
						classAccess.put(split[2], mergeAccess(access, classAccess.get(split[2])));
					} else {
						classAccess.put(split[2], access);
					}

					break;
				case "field":
					if (split.length != 5) {
						throw new RuntimeException("Failed to parse access escalator. at line:" + line);
					}

					addOrMerge(fieldAccess, new EntryTriple(split[2], split[3], split[4]), access);
					break;
				case "method":
					if (split.length != 5) {
						throw new RuntimeException("Failed to parse access escalator. at line:" + line);
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

	void addOrMerge(Map<EntryTriple, ChangeList> map, EntryTriple entry, ChangeList changeList) {
		for (Access access : changeList.getChanges()) {
			addOrMerge(map, entry, access);
		}
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
			case "mutable":
				return Access.MUTABLE;
			default:
				throw new UnsupportedOperationException("Unknown access:" + input);
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

		MUTABLE(true);

		private final boolean exclusive;

		Access(boolean exclusive) {
			this.exclusive = exclusive;
		}

		Access() {
			this(false);
		}

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
				case MUTABLE:
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
			if (access.exclusive && !changes.contains(access)) {
				changes.add(access);
			} else {
				if (changes.isEmpty() || changes.stream().allMatch(a -> a.exclusive)) {
					changes.add(access);
				} else {
					Access existing = null;

					for (Access change : changes) {
						if (!change.exclusive) {
							if (existing != null) {
								throw new RuntimeException("More than one change");
							}

							existing = change;
						}
					}

					if (existing == null) {
						throw new RuntimeException("Failed to find existing, something has gone wrong");
					}

					changes.remove(existing);
					changes.add(mergeAccess(existing, access));
				}
			}
		}

		public void merge(ChangeList changeList) {
			for (Access change : changeList.getChanges()) {
				add(change);
			}
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
