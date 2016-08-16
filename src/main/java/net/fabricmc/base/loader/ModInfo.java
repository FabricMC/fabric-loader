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

package net.fabricmc.base.loader;

import com.google.gson.*;
import net.shadowfacts.shadowlib.version.Version;
import net.shadowfacts.shadowlib.version.VersionMatcher;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModInfo {

//	Required
	private String id;
	private String group;
	private Version version;

//	Optional
	private String modClass = "";
	private String mixinConfig = "";
	private String title = "";
	private String description = "";
	private Links links = Links.EMPTY;
	private Dependency[] dependencies = new Dependency[0];
	private Person[] authors = new Person[0];
	private Person[] contributors = new Person[0];
	private String license = "";

	public String getId() {
		return id;
	}

	public String getGroup() {
		return group;
	}

	public Version getVersion() {
		return version;
	}

	public String getModClass() {
		return modClass;
	}

	public String getMixinConfig() {
		return mixinConfig;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Links getLinks() {
		return links;
	}

	public Dependency[] getDependencies() {
		return dependencies;
	}

	public Person[] getAuthors() {
		return authors;
	}

	public Person[] getContributors() {
		return contributors;
	}

	public String getLicense() {
		return license;
	}

	public static class Links {

		public static final Links EMPTY = new Links();

		private String homepage = "";
		private String issues = "";
		private String sources = "";

		public String getHomepage() {
			return homepage;
		}

		public String getIssues() {
			return issues;
		}

		public String getSources() {
			return sources;
		}
	}

	public static class Dependency {

		private boolean required;
		private String group;
		private String id;
		private String[] versionMatchers;

		public Dependency(boolean required, String group, String id, String[] versionMatchers) {
			this.required = required;
			this.group = group;
			this.id = id;
			this.versionMatchers = versionMatchers;
		}

		public boolean isRequired() {
			return required;
		}

		public String getGroup() {
			return group;
		}

		public String getId() {
			return id;
		}

		public String[] getVersionMatchers() {
			return versionMatchers;
		}

		public boolean satisfiedBy(ModInfo info) {
			if (required && group.equals(info.group) && id.equals(info.id)) {
				for (String s : versionMatchers) {
					if (!VersionMatcher.matches(s, info.version)) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		public static class Deserializer implements JsonDeserializer<Dependency> {

			@Override
			public Dependency deserialize(JsonElement element, Type resultType, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();

					boolean required;
					String group, id;
					String[] versionMatchers;

					JsonElement requiredEl = object.get("required");
					if (requiredEl.isJsonPrimitive() && requiredEl.getAsJsonPrimitive().isBoolean()) {
						required = requiredEl.getAsBoolean();
					} else {
						throw new JsonParseException("Expected required to be a boolean");
					}

					JsonElement identifierEl = object.get("id");
					if (identifierEl.isJsonArray()) {
						JsonArray array = identifierEl.getAsJsonArray();
						if (array.size() == 3) {
							JsonElement part0 = array.get(0);
							JsonElement part1 = array.get(1);
							JsonElement part2 = array.get(2);

							if (part0.isJsonPrimitive()) {
								group = part0.getAsString();
							} else {
								throw new RuntimeException("Expected dependency group to be a string");
							}
							if (part1.isJsonPrimitive()) {
								id = part1.getAsString();
							} else {
								throw new RuntimeException("Expected dependency id to be a string");
							}
							if (part2.isJsonPrimitive()) {
								versionMatchers = new String[]{part2.getAsString()};
							} else if (part2.isJsonArray()) {
								JsonArray versionsArray = part2.getAsJsonArray();
								versionMatchers = new String[versionsArray.size()];
								for (int i = 0; i < versionsArray.size(); i++) {
									versionMatchers[i] = versionsArray.get(i).getAsString();
								}
							} else {
								throw new RuntimeException("Expected dependency version to be a string");
							}

						} else {
							throw new JsonParseException("Expected id array to be of length 3");
						}
					} else {
						throw new JsonParseException("Expected id to be an array");
					}

					return new Dependency(required, group, id, versionMatchers);
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

}
