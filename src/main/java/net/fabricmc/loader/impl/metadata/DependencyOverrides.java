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

package net.fabricmc.loader.impl.metadata;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;

public final class DependencyOverrides {
	public static final DependencyOverrides INSTANCE = new DependencyOverrides();

	private final boolean exists;
	private final Map<String, List<Entry>> dependencyOverrides;

	private DependencyOverrides() {
		Path path = FabricLoaderImpl.INSTANCE.getConfigDir().resolve("fabric_loader_dependencies.json");
		exists = Files.exists(path);

		if (!exists) {
			dependencyOverrides = Collections.emptyMap();
			return;
		}

		try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
			dependencyOverrides = parse(reader);
		} catch (IOException | ParseMetadataException e) {
			throw new RuntimeException("Failed to parse " + path.toString(), e);
		}
	}

	private static Map<String, List<Entry>> parse(JsonReader reader) throws ParseMetadataException, IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Root must be an object", reader);
		}

		Map<String, List<Entry>> ret = new HashMap<>();
		reader.beginObject();

		if (!reader.nextName().equals("version")) {
			throw new ParseMetadataException("First key must be \"version\"", reader);
		}

		if (reader.peek() != JsonToken.NUMBER || reader.nextInt() != 1) {
			throw new ParseMetadataException("Unsupported \"version\", must be 1", reader);
		}

		while (reader.hasNext()) {
			String key = reader.nextName();

			if ("overrides".equals(key)) {
				reader.beginObject();

				while (reader.hasNext()) {
					String modId = reader.nextName();

					ret.put(modId, readKeys(reader));
				}

				reader.endObject();
			} else {
				throw new ParseMetadataException("Unsupported root key: " + key, reader);
			}
		}

		reader.endObject();

		return ret;
	}

	private static List<Entry> readKeys(JsonReader reader) throws IOException, ParseMetadataException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Dependency container must be an object!", reader);
		}

		Map<ModDependency.Kind, Map<Operation, List<ModDependency>>> modOverrides = new EnumMap<>(ModDependency.Kind.class);
		reader.beginObject();

		while (reader.hasNext()) {
			String key = reader.nextName();
			Operation op = null;

			for (Operation o : Operation.VALUES) {
				if (key.startsWith(o.operator)) {
					op = o;
					key = key.substring(o.operator.length());
					break;
				}
			}

			assert op != null; // should always match since REPLACE has an empty operator string

			ModDependency.Kind kind = ModDependency.Kind.parse(key);

			if (kind == null) {
				throw new ParseMetadataException(String.format("%s is not an allowed dependency key, must be one of: %s",
						key, Arrays.stream(ModDependency.Kind.values()).map(ModDependency.Kind::getKey).collect(Collectors.joining(", "))),
						reader);
			}

			List<ModDependency> deps = readDependencies(reader, kind);

			if (!deps.isEmpty() || op == Operation.REPLACE) {
				modOverrides.computeIfAbsent(kind, ignore -> new EnumMap<>(Operation.class)).put(op, deps);
			}
		}

		reader.endObject();

		List<Entry> ret = new ArrayList<>();

		for (Map.Entry<ModDependency.Kind, Map<Operation, List<ModDependency>>> entry : modOverrides.entrySet()) {
			ModDependency.Kind kind = entry.getKey();
			Map<Operation, List<ModDependency>> map = entry.getValue();

			List<ModDependency> values = map.get(Operation.REPLACE);

			if (values != null) {
				ret.add(new Entry(Operation.REPLACE, kind, values)); // suppresses add+remove
			} else {
				values = map.get(Operation.REMOVE);
				if (values != null) ret.add(new Entry(Operation.REMOVE, kind, values));

				values = map.get(Operation.ADD); // after remove
				if (values != null) ret.add(new Entry(Operation.ADD, kind, values));
			}
		}

		return ret;
	}

	private static List<ModDependency> readDependencies(JsonReader reader, ModDependency.Kind kind) throws IOException, ParseMetadataException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Dependency container must be an object!", reader);
		}

		List<ModDependency> ret = new ArrayList<>();
		reader.beginObject();

		while (reader.hasNext()) {
			final String modId = reader.nextName();
			final List<String> matcherStringList = new ArrayList<>();

			switch (reader.peek()) {
			case STRING:
				matcherStringList.add(reader.nextString());
				break;
			case BEGIN_ARRAY:
				reader.beginArray();

				while (reader.hasNext()) {
					if (reader.peek() != JsonToken.STRING) {
						throw new ParseMetadataException("Dependency version range array must only contain string values", reader);
					}

					matcherStringList.add(reader.nextString());
				}

				reader.endArray();
				break;
			default:
				throw new ParseMetadataException("Dependency version range must be a string or string array!", reader);
			}

			try {
				ret.add(new ModDependencyImpl(kind, modId, matcherStringList));
			} catch (VersionParsingException e) {
				throw new ParseMetadataException(e);
			}
		}

		reader.endObject();

		return ret;
	}

	public Collection<ModDependency> apply(String modId, Collection<ModDependency> dependencies) {
		if (!exists) return dependencies;

		List<Entry> modOverrides = dependencyOverrides.get(modId);
		if (modOverrides == null) return dependencies;

		List<ModDependency> ret = new ArrayList<>(dependencies);

		for (Entry entry : modOverrides) {
			switch (entry.operation) {
			case REPLACE:
				for (Iterator<ModDependency> it = ret.iterator(); it.hasNext(); ) {
					ModDependency dep = it.next();

					if (dep.getKind() == entry.kind) {
						it.remove();
					}
				}

				ret.addAll(entry.values);
				break;
			case REMOVE:
				for (Iterator<ModDependency> it = ret.iterator(); it.hasNext(); ) {
					ModDependency dep = it.next();

					if (dep.getKind() == entry.kind) {
						for (ModDependency value : entry.values) {
							if (value.getModId().equals(dep.getModId())) {
								it.remove();
								break;
							}
						}
					}
				}

				break;
			case ADD:
				ret.addAll(entry.values);
				break;
			}
		}

		return ret;
	}

	public Collection<String> getDependencyOverrides() {
		return dependencyOverrides.keySet();
	}

	private static final class Entry {
		final Operation operation;
		final ModDependency.Kind kind;
		final List<ModDependency> values;

		Entry(Operation operation, ModDependency.Kind kind, List<ModDependency> values) {
			this.operation = operation;
			this.kind = kind;
			this.values = values;
		}
	}

	private enum Operation {
		ADD("+"),
		REMOVE("-"),
		REPLACE(""); // needs to be last to properly match the operator (empty string would match everything)

		static final Operation[] VALUES = values();

		final String operator;

		Operation(String operator) {
			this.operator = operator;
		}
	}
}
