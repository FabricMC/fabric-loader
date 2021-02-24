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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

final class V0ModMetadata extends AbstractModMetadata implements LoaderModMetadata {
	private static final Mixins EMPTY_MIXINS = new Mixins(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	// Required
	private final String id;
	private final Version version;

	// Optional (Environment)
	private final Map<String, ModDependency> requires;
	private final Map<String, ModDependency> suggests;
	private final Map<String, ModDependency> conflicts;
	private final Map<String, ModDependency> breaks;
	private final String languageAdapter = "net.fabricmc.loader.language.JavaLanguageAdapter"; // TODO: Constants class?
	private final Mixins mixins;
	private final ModEnvironment environment; // REMOVEME: Replacing Side in old metadata with this
	private final String initializer;
	private final Collection<String> initializers;

	// Optional (metadata)
	private final String name;
	private final String description;
	private final Map<String, ModDependency> recommends;
	private final Collection<Person> authors;
	private final Collection<Person> contributors;
	private final ContactInformation links;
	private final String license;

	V0ModMetadata(String id, Version version, Map<String, ModDependency> requires, Map<String, ModDependency> conflicts, Mixins mixins, ModEnvironment environment, String initializer, Collection<String> initializers, String name, String description, Map<String, ModDependency> recommends, Collection<Person> authors, Collection<Person> contributors, ContactInformation links, String license) {
		this.id = id;
		this.version = version;
		this.requires = DependencyOverrides.INSTANCE.getActiveDependencyMap("depends", id, Collections.unmodifiableMap(requires));
		this.recommends = DependencyOverrides.INSTANCE.getActiveDependencyMap("recommends", id, Collections.emptyMap());
		this.suggests = DependencyOverrides.INSTANCE.getActiveDependencyMap("suggests", id, Collections.unmodifiableMap(recommends));
		this.conflicts = DependencyOverrides.INSTANCE.getActiveDependencyMap("conflicts", id, Collections.emptyMap());
		this.breaks = DependencyOverrides.INSTANCE.getActiveDependencyMap("breaks", id, Collections.unmodifiableMap(conflicts));

		if (mixins == null) {
			this.mixins = V0ModMetadata.EMPTY_MIXINS;
		} else {
			this.mixins = mixins;
		}

		this.environment = environment;
		this.initializer = initializer;
		this.initializers = Collections.unmodifiableCollection(initializers);
		this.name = name;

		if (description == null) {
			this.description = "";
		} else {
			this.description = description;
		}

		this.authors = Collections.unmodifiableCollection(authors);
		this.contributors = contributors;
		this.links = links;
		this.license = license;
	}

	@Override
	public int getSchemaVersion() {
		return 0;
	}

	@Override
	public String getType() {
		return "fabric";
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Collection<String> getProvides() {
		return Collections.emptyList();
	}

	@Override
	public Version getVersion() {
		return this.version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		return this.environment;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return this.environment.matches(type);
	}

	@Override
	public Collection<ModDependency> getDepends() {
		return this.requires.values();
	}

	@Override
	public Collection<ModDependency> getRecommends() {
		return this.recommends.values();
	}

	@Override
	public Collection<ModDependency> getSuggests() {
		return this.suggests.values();
	}

	@Override
	public Collection<ModDependency> getConflicts() {
		return this.conflicts.values();
	}

	@Override
	public Collection<ModDependency> getBreaks() {
		return this.breaks.values();
	}

	// General metadata

	@Override
	public String getName() {
		if (this.name != null && this.name.isEmpty()) {
			return this.id;
		}

		return this.name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public Collection<Person> getAuthors() {
		return this.authors;
	}

	@Override
	public Collection<Person> getContributors() {
		return this.contributors;
	}

	@Override
	public ContactInformation getContact() {
		return this.links;
	}

	@Override
	public Collection<String> getLicense() {
		return Collections.singleton(this.license);
	}

	@Override
	public Optional<String> getIconPath(int size) {
		// honor Mod Menu's de-facto standard
		return Optional.of("assets/" + this.getId() + "/icon.png");
	}

	@Override
	public String getOldStyleLanguageAdapter() {
		return this.languageAdapter;
	}

	@Override
	public Map<String, CustomValue> getCustomValues() { return Collections.emptyMap(); }

	@Override
	public boolean containsCustomValue(String key) {
		return false;
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return null;
	}

	// Internals

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
		if (this.initializer != null) {
			return Collections.singletonList(this.initializer);
		} else if (!this.initializers.isEmpty()) {
			return this.initializers;
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
		List<String> mixinConfigs = new ArrayList<>(this.mixins.common);

		switch (type) {
		case CLIENT:
			mixinConfigs.addAll(this.mixins.client);
			break;
		case SERVER:
			mixinConfigs.addAll(this.mixins.server);
			break;
		}

		return mixinConfigs;
	}

	@Override
	public String getAccessWidener() {
		return null; // intentional null
	}

	static final class Mixins {
		final Collection<String> client;
		final Collection<String> common;
		final Collection<String> server;

		private Mixins() {
			this.client = Collections.emptyList();
			this.common = Collections.emptyList();
			this.server = Collections.emptyList();
		}

		Mixins(Collection<String> client, Collection<String> common, Collection<String> server) {
			this.client = Collections.unmodifiableCollection(client);
			this.common = Collections.unmodifiableCollection(common);
			this.server = Collections.unmodifiableCollection(server);
		}
	}
}
