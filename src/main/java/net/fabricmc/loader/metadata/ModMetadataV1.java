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

package net.fabricmc.loader.metadata;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.util.version.VersionParsingException;
import net.fabricmc.loader.util.version.VersionPredicateParser;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Definition class for "fabric.mod.json" files.
 */
public class ModMetadataV1 extends AbstractModMetadata implements LoaderModMetadata {
	// Required
	private String id;
	private Version version;

	// Optional (mod loading)
	private Environment environment = Environment.UNIVERSAL;
	private EntrypointContainer entrypoints = new EntrypointContainer();
	private JarEntry[] jars = new JarEntry[0];
	private MixinEntry[] mixins = new MixinEntry[0];
	private String accessWidener;

	// Optional (dependency resolution)
	private DependencyContainer depends = new DependencyContainer();
	private DependencyContainer recommends = new DependencyContainer();
	private DependencyContainer suggests = new DependencyContainer();
	private DependencyContainer conflicts = new DependencyContainer();
	private DependencyContainer breaks = new DependencyContainer();

	// Optional (metadata)
	private String name;
	private String description = "";
	private Person[] authors = new Person[0];
	private Person[] contributors = new Person[0];
	private Map<String, String> contact = new HashMap<>();
	private LicenseEntry license = new LicenseEntry();
	private IconEntry icon = new IconEntry();

	// Optional (language adapter providers)
	private Map<String, String> languageAdapters = new HashMap<>();

	// Optional (custom)
	private Map<String, JsonElement> custom = new HashMap<>();

	// Happy little accidents
	@Deprecated
	private DependencyContainer requires = new DependencyContainer();

	@Override
	public String getType() {
		return "fabric";
	}

	@Override
	public int getSchemaVersion() {
		return 1;
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		return languageAdapters;
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return Arrays.asList(jars);
	}

	@Override
	public Collection<String> getOldInitializers() {
		return Collections.emptyList();
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		List<EntrypointMetadata> list = entrypoints.metadataMap.get(type);
		return list != null ? list : Collections.emptyList();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return entrypoints.metadataMap.keySet();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void emitFormatWarnings(Logger logger) {
		if (!requires.dependencies.isEmpty()) {
			logger.warn("Mod `" + id + "` (" + version + ") uses 'requires' key in fabric.mod.json, which is not supported - use 'depends'");
		}
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		return Arrays.asList(mixins).stream()
			.filter((e) -> e.environment.matches(type))
			.map((e) -> e.config)
			.collect(Collectors.toList());
	}

	@Override
	public String getAccessWidener() {
		return accessWidener;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return environment.matches(type);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		if (name == null || name.isEmpty()) {
			return id;
		}
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public Collection<net.fabricmc.loader.api.metadata.Person> getAuthors() {
		return Arrays.asList(authors);
	}

	@Override
	public Collection<net.fabricmc.loader.api.metadata.Person> getContributors() {
		return Arrays.asList(contributors);
	}

	@Override
	public ContactInformation getContact() {
		return new MapBackedContactInformation(contact);
	}

	@Override
	public Collection<String> getLicense() {
		return license.entries;
	}

	@Override
	public Optional<String> getIconPath(int size) {
		if (icon.iconMap != null && !icon.iconMap.isEmpty()) {
			int iconValue = -1;

			for (int i : icon.iconMap.keySet()) {
				iconValue = i;
				if (iconValue >= size) {
					break;
				}
			}

			return Optional.of(icon.iconMap.get(iconValue));
		} else {
			return Optional.ofNullable(icon.icon);
		}
	}

	@Override
	public boolean containsCustomValue(String key) {
		return custom.containsKey(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		JsonElement e = custom.get(key);
		if (e == null) return null;

		return CustomValueImpl.fromJsonElement(e);
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public Collection<ModDependency> getDepends() {
		return depends.dependencies;
	}

	@Override
	public Collection<ModDependency> getRecommends() {
		return recommends.dependencies;
	}

	@Override
	public Collection<ModDependency> getSuggests() {
		return suggests.dependencies;
	}

	@Override
	public Collection<ModDependency> getConflicts() {
		return conflicts.dependencies;
	}

	@Override
	public Collection<ModDependency> getBreaks() {
		return breaks.dependencies;
	}

	public static class DependencyContainer {
		private final Map<String, List<String>> matcherStrings = new HashMap<>();
		private final List<ModDependency> dependencies = new ArrayList<>();

		public static class Deserializer implements JsonDeserializer<DependencyContainer> {
			@Override
			public DependencyContainer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				if (!json.isJsonObject()) {
					throw new RuntimeException("Dependency container must be an object!");
				}

				DependencyContainer ctr = new DependencyContainer();
				JsonObject obj = json.getAsJsonObject();

				for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
					List<String> matcherStringList = new ArrayList<>();

					if (entry.getValue().isJsonPrimitive()) {
						matcherStringList.add(entry.getValue().getAsString());
					} else if (entry.getValue().isJsonArray()) {
						JsonArray array = entry.getValue().getAsJsonArray();
						array.forEach((e) -> matcherStringList.add(e.getAsString()));
					} else {
						throw new RuntimeException("Dependency version range must be a string or string array!");
					}

					String id = entry.getKey();
					ctr.matcherStrings.put(id, matcherStringList);

					String depAsStr;
					{
						StringBuilder builder = new StringBuilder("{");
						builder.append(id);
						builder.append(" @ [");
						for (int i = 0; i < matcherStringList.size(); i++) {
							if (i > 0) {
								builder.append(" || ");
							}
							builder.append(matcherStringList.get(i));
						}
						builder.append("]}");

						depAsStr = builder.toString();
					}

					ctr.dependencies.add(new ModDependency() {
						@Override
						public String getModId() {
							return id;
						}

						@Override
						public boolean matches(Version version) {
							for (String s : matcherStringList) {
								try {
									if (VersionPredicateParser.matches(version, s)) {
										return true;
									}
								} catch (VersionParsingException e) {
									e.printStackTrace();
									return false;
								}
							}

							return false;
						}

						@Override
						public String toString() {
							return depAsStr;
						}
					});
				}

				return ctr;
			}
		}
	}

	public static class Person implements net.fabricmc.loader.api.metadata.Person {
		private String name;
		private MapBackedContactInformation contact = new MapBackedContactInformation(Collections.emptyMap());

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ContactInformation getContact() {
			return contact;
		}

		public static class Deserializer implements JsonDeserializer<Person> {
			@Override
			public Person deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				Person person = new Person();

				if (json.isJsonObject()) {
					JsonObject obj = json.getAsJsonObject();
					if (!obj.has("name")) {
						throw new JsonParseException("Person object must have a 'name' field!");
					}

					person.name = obj.get("name").getAsString();
					if (obj.has("contact")) {
						person.contact = new MapBackedContactInformation(
							context.deserialize(obj.get("contact"), TypeToken.getParameterized(HashMap.class, String.class, String.class).getType())
						);
					}
				} else if (json.isJsonPrimitive()) {
					person.name = json.getAsString();
				} else {
					throw new JsonParseException("Person type must be an object or string!");
				}

				return person;
			}
		}
	}

	public static class JarEntry implements NestedJarEntry {
		private String file;

		@Override
		public String getFile() {
			return file;
		}

		public static class Deserializer implements JsonDeserializer<JarEntry> {
			@Override
			public JarEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				JarEntry entry = new JarEntry();
				if (json.isJsonObject()) {
					JsonObject obj = json.getAsJsonObject();
					if (!obj.has("file")) {
						throw new JsonParseException("Missing mandatory key 'file' in JAR entry!");
					}

					entry.file = obj.get("file").getAsString();
				} else {
					throw new JsonParseException("Invalid type for JAR entry!");
				}

				return entry;
			}
		}
	}

	public static class IconEntry {
		private String icon;
		private SortedMap<Integer, String> iconMap;

		public static class Deserializer implements JsonDeserializer<IconEntry> {
			@Override
			public IconEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				IconEntry entry = new IconEntry();
				if (json.isJsonPrimitive()) {
					entry.icon = json.getAsString();
				} else if (json.isJsonObject()) {
					entry.iconMap = new TreeMap<>(Comparator.naturalOrder());

					JsonObject obj = json.getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
						int size;
						try {
							size = Integer.parseInt(e.getKey());
						} catch (NumberFormatException ex) {
							throw new JsonParseException("Could not parse icon size '" + e.getKey() + "'!", ex);
						}

						if (size < 1) {
							throw new JsonParseException("Size must be positive!");
						} else if (!e.getValue().isJsonPrimitive()) {
							throw new JsonParseException("Icon value must be a string!");
						}

						entry.iconMap.put(size, e.getValue().getAsString());
					}

					if (entry.iconMap.isEmpty()) {
						throw new JsonParseException("Icon object must not be empty!");
					}
				} else {
					throw new JsonParseException("Icon entry must be an object or string!");
				}

				return entry;
			}
		}
	}

	public static class EntrypointContainer {
		private final Map<String, List<EntrypointMetadata>> metadataMap = new HashMap<>();

		static class Metadata implements EntrypointMetadata {
			private final String adapter;
			private final String value;

			Metadata(String adapter, String value) {
				this.adapter = adapter;
				this.value = value;
			}

			@Override
			public String getAdapter() {
				return adapter;
			}

			@Override
			public String getValue() {
				return value;
			}
		}

		public static class Deserializer implements JsonDeserializer<EntrypointContainer> {
			@Override
			public EntrypointContainer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				if (!json.isJsonObject()) {
					throw new JsonParseException("Entrypoints must be an object!");
				}

				JsonObject obj = json.getAsJsonObject();
				EntrypointContainer ctr = new EntrypointContainer();

				for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
					String key = entry.getKey();
					List<EntrypointMetadata> metadata = new ArrayList<>();

					if (entry.getValue().isJsonArray()) {
						for (JsonElement element : entry.getValue().getAsJsonArray()) {
							if (element.isJsonObject()) {
								JsonObject entObj = element.getAsJsonObject();
								String adapter = entObj.has("adapter") ? entObj.get("adapter").getAsString() : "default";
								String value = entObj.get("value").getAsString();

								metadata.add(new Metadata(adapter, value));
							} else {
								metadata.add(new Metadata("default", element.getAsString()));
							}
						}
					} else {
						throw new JsonParseException("Entrypoint list must be an array!");
					}

					if (!metadata.isEmpty()) {
						ctr.metadataMap.computeIfAbsent(key, (t) -> new ArrayList<>()).addAll(metadata);
					}
				}

				return ctr;
			}
		}
	}

	public static class MixinEntry {
		private String config;
		private Environment environment = Environment.UNIVERSAL;

		public static class Deserializer implements JsonDeserializer<MixinEntry> {
			@Override
			public MixinEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				MixinEntry entry = new MixinEntry();
				if (json.isJsonPrimitive()) {
					entry.config = json.getAsString();
				} else if (json.isJsonObject()) {
					JsonObject obj = json.getAsJsonObject();
					if (!obj.has("config")) {
						throw new JsonParseException("Missing mandatory key 'config' in mixin entry!");
					}

					entry.config = obj.get("config").getAsString();
					if (obj.has("environment")) {
						entry.environment = context.deserialize(obj.get("environment"), Environment.class);
					}
				} else {
					throw new JsonParseException("Invalid type for mixin entry!");
				}

				return entry;
			}
		}
	}

	public enum Environment {
		CLIENT,
		SERVER,
		UNIVERSAL;

		public boolean matches(EnvType type) {
			switch (this) {
				case CLIENT:
					return type == EnvType.CLIENT;
				case SERVER:
					return type == EnvType.SERVER;
				case UNIVERSAL:
					return true;
				default:
					return false;
			}
		}

		public static class Deserializer implements JsonDeserializer<Environment> {
			@Override
			public Environment deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				String s = element.getAsString().toLowerCase(Locale.ROOT);
				if (s.isEmpty() || s.equals("*")) {
					return UNIVERSAL;
				} else if (s.equals("client")) {
					return CLIENT;
				} else if (s.equals("server")) {
					return SERVER;
				} else {
					throw new JsonParseException("Invalid environment type: " + s + "!");
				}
			}
		}
	}

	public static class LicenseEntry {
		private final List<String> entries = new ArrayList<>();

		public static class Deserializer implements JsonDeserializer<LicenseEntry> {
			@Override
			public LicenseEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				LicenseEntry entry = new LicenseEntry();

				if (json.isJsonArray()) {
					json.getAsJsonArray().forEach((e) -> entry.entries.add(e.getAsString()));
				} else if (json.isJsonPrimitive()) {
					entry.entries.add(json.getAsString());
				} else {
					throw new JsonParseException("License must be a string or array of strings!");
				}

				return entry;
			}
		}
	}
}
