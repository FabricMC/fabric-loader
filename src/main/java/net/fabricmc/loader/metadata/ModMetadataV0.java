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
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.Version;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Definition class for "fabric.mod.json" files.
 */
public class ModMetadataV0 extends AbstractModMetadata implements LoaderModMetadata {
	// Required
	private String id;
	private Version version;

	// Optional (environment)
	private DependencyMap requires = new DependencyMap();
	private DependencyMap conflicts = new DependencyMap();
	private String languageAdapter = "net.fabricmc.loader.language.JavaLanguageAdapter";
	private Mixins mixins = Mixins.EMPTY;
	private Side side = Side.UNIVERSAL;
	private boolean lazilyLoaded = false;
	private String initializer;
	private String[] initializers;

	// Optional (metadata)
	private String name;
	private String description = "";
	private Links links = Links.EMPTY;
	private DependencyMap recommends = new DependencyMap();
	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private Person[] authors = new Person[0];
	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private Person[] contributors = new Person[0];
	private String license = "";

	@Override
	public int getSchemaVersion() {
		return 0;
	}

	@Override
	public String getOldStyleLanguageAdapter() {
		return languageAdapter;
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getOldInitializers() {
		if (initializer != null) {
			if (initializers != null) {
				throw new RuntimeException("initializer and initializers should not be set at the same time! (mod ID '" + id + "')");
			}

			return Collections.singletonList(initializer);
		} else if (initializers != null) {
			return Arrays.asList(initializers);
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return Collections.emptyList();
	}

	@Override
	public void emitFormatWarnings(Logger logger) {

	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		List<String> mixinConfigs = new ArrayList<>(Arrays.asList(mixins.common));
		if (type == EnvType.CLIENT) {
			mixinConfigs.addAll(Arrays.asList(mixins.client));
		} else if (type == EnvType.SERVER) {
			mixinConfigs.addAll(Arrays.asList(mixins.server));
		}
		return mixinConfigs;
	}

	@Override
	public String getAccessWidener() {
		return null;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		switch (side) {
			case UNIVERSAL:
				return true;
			case CLIENT:
				return type == EnvType.CLIENT;
			case SERVER:
				return type == EnvType.SERVER;
			default:
				return false;
		}
	}

	@Override
	public String getType() {
		return "fabric";
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
		return links;
	}

	@Override
	public Collection<String> getLicense() {
		return Collections.singletonList(license);
	}

	@Override
	public Optional<String> getIconPath(int size) {
		// honor Mod Menu's de-facto standard
		return Optional.of("assets/" + getId() + "/icon.png");
	}

	@Override
	public boolean containsCustomValue(String key) {
		return false;
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return null;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public Collection<ModDependency> getDepends() {
		return requires.toModDependencies();
	}

	@Override
	public Collection<ModDependency> getRecommends() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getSuggests() {
		return recommends.toModDependencies();
	}

	@Override
	public Collection<ModDependency> getConflicts() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getBreaks() {
		return conflicts.toModDependencies();
	}

	public static class Mixins {
		public static final Mixins EMPTY = new Mixins();

		private String[] client = {};
		private String[] common = {};
		private String[] server = {};

		public String[] getClient() {
			return client;
		}

		public String[] getCommon() {
			return common;
		}

		public String[] getServer() {
			return server;
		}

		public static class Deserializer implements JsonDeserializer<Mixins> {
			@Override
			public Mixins deserialize(JsonElement element, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				Mixins mixins = new Mixins();

				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();
					mixins.client = getStringArray(object, "client");
					mixins.common = getStringArray(object, "common");
					mixins.server = getStringArray(object, "server");
				} else {
					throw new JsonParseException("Expected mixins to be an object.");
				}


				return mixins;
			}

			private String[] getStringArray(JsonObject object, String name) throws JsonParseException {
				JsonElement element = object.get(name);
				if (element == null || element.isJsonNull()) {
					return new String[0];
				} else if (element.isJsonPrimitive()) {
					return new String[]{ element.getAsString() };
				} else if (element.isJsonArray()) {
					JsonArray array = element.getAsJsonArray();
					String[] strings = new String[array.size()];
					for (int i = 0; i < array.size(); i++) {
						strings[i] = array.get(i).getAsString();
					}
					return strings;
				} else {
					throw new JsonParseException("Expected " + name + " to be a string or an array of strings");
				}
			}
		}
	}

	public static class Links extends MapBackedContactInformation {
		public static final Links EMPTY = new Links(Collections.emptyMap());

		public Links(Map<String, String> map) {
			super(map);
		}

		public static class Deserializer implements JsonDeserializer<Links> {
			@Override
			public Links deserialize(JsonElement element, Type resultType, JsonDeserializationContext context) throws JsonParseException {
				Map<String, String> map = new HashMap<>();

				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();
					if (object.has("homepage")) map.put("homepage", object.get("homepage").getAsString());
					if (object.has("issues")) map.put("issues", object.get("issues").getAsString());
					if (object.has("sources")) map.put("sources", object.get("sources").getAsString());
				} else if (element.isJsonPrimitive()) {
					map.put("homepage", element.getAsString());
				} else {
					throw new JsonParseException("Expected links to be an object or string");
				}

				return new Links(map);
			}
		}
	}

	public static class DependencyMap extends HashMap<String, Dependency> {
		private List<ModDependency> modDepList;

		Collection<ModDependency> toModDependencies() {
			if (modDepList == null) {
				List<ModDependency> list = new ArrayList<>(this.size());
				for (String s : this.keySet()) {
					list.add(new ModDependency() {
						@Override
						public String getModId() {
							return s;
						}

						@Override
						public boolean matches(Version version) {
							return DependencyMap.this.get(s).satisfiedBy(version);
						}

						@Override
						public String toString() {
							String[] matchers = DependencyMap.this.get(s).versionMatchers;
							if (matchers.length == 0) {
								return getModId();
							} else if (matchers.length == 1) {
								return getModId() + " @ " + matchers[0];
							} else {
								return getModId() + " @ "+Arrays.toString(matchers);
							}
						}
					});
				}
				modDepList = Collections.unmodifiableList(list);
			}

			return modDepList;
		}
	}

	public static class Dependency {
		private String[] versionMatchers;
		private Side side;

		public Dependency(String[] versionMatchers, Side side) {
			this.versionMatchers = versionMatchers;
			this.side = side;
		}

		public String[] getVersionMatchers() {
			return versionMatchers;
		}

		public Side getSide() {
			return side;
		}

		public boolean satisfiedBy(Version version) {
			/* try {
				for (String s : versionMatchers) {
					if (!VersionPredicateParser.matches(version, s)) {
						return false;
					}
				}

				return true;
			} catch (VersionParsingException e) {
				e.printStackTrace();
				return false;
			} */
			return true;
		}

		@Override
		public String toString() {
			return Arrays.toString(versionMatchers);
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
					Side side = Side.UNIVERSAL;

					if (object.has("side")) {
						JsonElement sideEl = object.get("side");
						side = context.deserialize(sideEl, Side.class);
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
					return new Dependency(versionMatchers, Side.UNIVERSAL);
				}

				throw new JsonParseException("Expected dependency to be an object");
			}
		}
	}

	public static class Person implements net.fabricmc.loader.api.metadata.Person {
		private final String name;
		private final MapBackedContactInformation contact;

		public Person(String name, String email, String website) {
			this.name = name;
			Map<String, String> contactMap = new HashMap<>();
			if (email != null) {
				contactMap.put("email", email);
			}
			if (website != null) {
				contactMap.put("website", website);
			}
			this.contact = new MapBackedContactInformation(contactMap);
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public ContactInformation getContact() {
			return contact;
		}

		public static class Deserializer implements JsonDeserializer<Person> {

			private static final Pattern WEBSITE_PATTERN = Pattern.compile("\\((.+)\\)");
			private static final Pattern EMAIL_PATTERN = Pattern.compile("<(.+)>");

			@Override
			public Person deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				if (element.isJsonPrimitive()) {
					String person = element.getAsString();
					String[] parts = person.split(" ");

					String name, email = "", website = "";

					Matcher websiteMatcher = WEBSITE_PATTERN.matcher(parts[parts.length - 1]);
					if (websiteMatcher.matches()) {
						website = websiteMatcher.group(1);
						parts = Arrays.copyOf(parts, parts.length - 1);
					}

					Matcher emailMatcher = EMAIL_PATTERN.matcher(parts[parts.length - 1]);
					if (emailMatcher.matches()) {
						email = emailMatcher.group(1);
						parts = Arrays.copyOf(parts, parts.length - 1);
					}

					name = String.join(" ", parts);

					return new Person(name, email, website);
				} else if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();
					String name = object.has("name") ? object.get("name").getAsString() : "";
					String email = object.has("email") ? object.get("email").getAsString() : "";
					String website = object.has("website") ? object.get("website").getAsString() : "";

					return new Person(name, email, website);
				}
				throw new RuntimeException("Expected person to be a string");
			}
		}
	}

	public enum Side {
		CLIENT,
		SERVER,
		UNIVERSAL;

		public boolean hasClient() {
			return this != SERVER;
		}

		public boolean hasServer() {
			return this != CLIENT;
		}

		public boolean isClient() {
			return this == CLIENT;
		}

		public boolean isServer() {
			return this == SERVER;
		}

		public static class Deserializer implements JsonDeserializer<Side> {
			@Override
			public Side deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				return valueOf(element.getAsString().toUpperCase(Locale.ROOT));
			}
		}
	}
}
