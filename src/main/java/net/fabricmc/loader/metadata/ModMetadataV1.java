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

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.util.version.VersionParsingException;
import net.fabricmc.loader.util.version.VersionPredicateParser;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Definition class for "fabric.mod.json" files.
 */
public class ModMetadataV1 implements LoaderModMetadata {
	// Required
	private String id;
	private Version version;

	// Optional (mod loading)
	private Environment environment = Environment.UNIVERSAL;
	private String[] initializers = new String[0];
	private JarEntry[] jars = new JarEntry[0];
	private MixinEntry[] mixins = new MixinEntry[0];

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

	// Optional (language adapter providers)
	private Map<String, String> languageAdapters = new HashMap<>();

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
	public Collection<String> getInitializers() {
		return Arrays.asList(initializers);
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		return Arrays.asList(mixins).stream()
			.filter((e) -> e.environment.matches(type))
			.map((e) -> e.config)
			.collect(Collectors.toList());
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
					ctr.dependencies.add(new ModDependency() {
						@Override
						public String getModId() {
							return id;
						}

						@Override
						public boolean matches(Version version) {
							for (String s : matcherStringList) {
								try {
									if (!VersionPredicateParser.matches(version, s)) {
										return false;
									}
								} catch (VersionParsingException e) {
									e.printStackTrace();
									return false;
								}
							}

							return true;
						}
					});
				}

				return ctr;
			}
		}
	}

	public static class Dependency {
		private String[] versionMatchers;
		private Environment side;

		public Dependency(String[] versionMatchers, Environment side) {
			this.versionMatchers = versionMatchers;
			this.side = side;
		}

		public String[] getVersionMatchers() {
			return versionMatchers;
		}

		public Environment getSide() {
			return side;
		}

		public boolean satisfiedBy(Version version) {
			try {
				for (String s : versionMatchers) {
					if (!VersionPredicateParser.matches(version, s)) {
						return false;
					}
				}

				return true;
			} catch (VersionParsingException e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		public String toString() {
			return "[" + Joiner.on(", ").join(versionMatchers) + "]";
		}

		public static class Deserializer implements JsonDeserializer<Dependency> {
			private String[] deserializeVersionMatchers(JsonElement versionEl) {
				String[] versionMatchers;

				if (versionEl.isJsonPrimitive()) {
					versionMatchers = new String[] { versionEl.getAsString() };
				} else if (versionEl.isJsonArray()) {
					JsonArray array = versionEl.getAsJsonArray();
					versionMatchers = new String[array.size()];
					for (int i = 0; i < array.size(); i++) {
						versionMatchers[i] = array.get(i).getAsString();
					}
				} else {
					throw new JsonParseException("Expected version to be a string or array");
				}

				return versionMatchers;
			}

			@Override
			public Dependency deserialize(JsonElement element, Type resultType, JsonDeserializationContext context) throws JsonParseException {
				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();

					String[] versionMatchers;
					Environment side = Environment.UNIVERSAL;

					if (object.has("side")) {
						JsonElement sideEl = object.get("side");
						side = context.deserialize(sideEl, Environment.class);
					}

					if (object.has("version")) {
						JsonElement versionEl = object.get("version");
						versionMatchers = deserializeVersionMatchers(versionEl);
					} else {
						throw new JsonParseException("Missing version element");
					}

					return new Dependency(versionMatchers, side);
				} else if (element.isJsonPrimitive() || element.isJsonArray()) {
					String[] versionMatchers = deserializeVersionMatchers(element);
					return new Dependency(versionMatchers, Environment.UNIVERSAL);
				}

				throw new JsonParseException("Expected dependency to be an object");
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
							context.deserialize(obj.get("contact"), TypeUtils.parameterize(HashMap.class, String.class, String.class))
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
				if (json.isJsonPrimitive()) {
					entry.file = json.getAsString();
				} else if (json.isJsonObject()) {
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
