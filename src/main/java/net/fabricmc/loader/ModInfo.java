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

package net.fabricmc.loader;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Definition class for "fabric.mod.json" files.
 */
public class ModInfo {

	// Required
	private String id;
	private String name;
	private String version;

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
	private String description = "";
	private Links links = Links.EMPTY;
	private DependencyMap recommends = new DependencyMap();
	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private Person[] authors = new Person[0];
	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private Person[] contributors = new Person[0];
	private String license = "";

	public List<String> getInitializers() {
		if (initializer != null) {
			if (initializers != null) {
				throw new RuntimeException("initializer and initializers should not be set at the same time! (mod " + id + ")");
			}

			return Collections.singletonList(initializer);
		} else if (initializers != null) {
			return Arrays.asList(initializers);
		} else {
			return Collections.emptyList();
		}
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getVersionString() {
		return version;
	}

	public String getLanguageAdapter() {
		return languageAdapter;
	}

	public Mixins getMixins() {
		return mixins;
	}

	public Side getSide() {
		return side;
	}

	public boolean isLazilyLoaded() {
		return lazilyLoaded;
	}

	public String getDescription() {
		return description;
	}

	public Links getLinks() {
		return links;
	}

	public DependencyMap getRequires() {
		return requires;
	}

	public DependencyMap getRecommends() {
		return recommends;
	}

	public DependencyMap getConflicts() {
		return conflicts;
	}

	public List<Person> getAuthors() {
		return Arrays.asList(authors);
	}

	public List<Person> getContributors() {
		return Arrays.asList(contributors);
	}

	public String getLicense() {
		return license;
	}

	public static class Mixins {
		public static final Mixins EMPTY = new Mixins();

		private String client;
		private String common;
		private String server;

		public String getClient() {
			return client;
		}

		public String getCommon() {
			return common;
		}

		public String getServer() {
			return server;
		}
	}

	public static class Links {
		public static final Links EMPTY = new Links();

		private String homepage;
		private String issues;
		private String sources;

		public String getHomepage() {
			return homepage;
		}

		public String getIssues() {
			return issues;
		}

		public String getSources() {
			return sources;
		}

		public static class Deserializer implements JsonDeserializer<Links> {
			@Override
			public Links deserialize(JsonElement element, Type resultType, JsonDeserializationContext context) throws JsonParseException {
				Links links = new Links();

				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();
					links.homepage = object.has("homepage") ? object.get("homepage").getAsString() : "";
					links.issues = object.has("issues") ? object.get("issues").getAsString() : "";
					links.sources = object.has("sources") ? object.get("sources").getAsString() : "";
				} else if (element.isJsonPrimitive()) {
					links.homepage = element.getAsString();
				} else {
					throw new JsonParseException("Expected links to be an object or string");
				}

				return links;
			}
		}
	}

	public static class DependencyMap extends HashMap<String, Dependency> {

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

		public boolean satisfiedBy(ModInfo info) {
			// TODO: Actually implement this once we decide on an implementation.
			/* for (String s : versionMatchers) {
				if (!info.version.equals(s)) {
					return false;
				}
			} */
			return true;
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
					boolean required = true;
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

	public static class Person {

		private String name;
		private String email;
		private String website;

		public Person(String name, String email, String website) {
			this.name = name;
			this.email = email;
			this.website = website;
		}

		public String getName() {
			return name;
		}

		public String getEmail() {
			return email;
		}

		public String getWebsite() {
			return website;
		}

		public static class Deserializer implements JsonDeserializer<Person> {

			private static final Pattern WEBSITE_PATTERN = Pattern.compile("\\((.+)\\)");
			private static final Pattern EMAIL_PATTERN = Pattern.compile("<(.+)>");

			@Override
			public Person deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				if (element.isJsonPrimitive()) {
					String person = element.getAsString();
					List<String> parts = Arrays.asList(person.split(" "));

					String name, email = "", website = "";

					Matcher websiteMatcher = WEBSITE_PATTERN.matcher(parts.get(parts.size() - 1));
					if (websiteMatcher.matches()) {
						website = websiteMatcher.group(1);
						parts.remove(parts.size() - 1);
					}

					Matcher emailMatcher = EMAIL_PATTERN.matcher(parts.get(parts.size() - 1));
					if (emailMatcher.matches()) {
						email = emailMatcher.group(1);
						parts.remove(parts.size() - 1);
					}

					name = String.join(" ", parts);

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

	}
}
