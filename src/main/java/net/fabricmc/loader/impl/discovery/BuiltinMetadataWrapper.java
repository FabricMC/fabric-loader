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

package net.fabricmc.loader.impl.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;

class BuiltinMetadataWrapper extends AbstractModMetadata implements LoaderModMetadata {
	private final ModMetadata parent;

	BuiltinMetadataWrapper(ModMetadata parent) {
		this.parent = parent;
	}

	@Override
	public String getType() {
		return parent.getType();
	}

	@Override
	public String getId() {
		return parent.getId();
	}

	@Override
	public Collection<String> getProvides() {
		return parent.getProvides();
	}

	@Override
	public Version getVersion() {
		return parent.getVersion();
	}

	@Override
	public ModEnvironment getEnvironment() {
		return parent.getEnvironment();
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return parent.getDependencies();
	}

	@Override
	public String getName() {
		return parent.getName();
	}

	@Override
	public String getDescription() {
		return parent.getDescription();
	}

	@Override
	public Collection<Person> getAuthors() {
		return parent.getAuthors();
	}

	@Override
	public Collection<Person> getContributors() {
		return parent.getContributors();
	}

	@Override
	public ContactInformation getContact() {
		return parent.getContact();
	}

	@Override
	public Collection<String> getLicense() {
		return parent.getLicense();
	}

	@Override
	public Optional<String> getIconPath(int size) {
		return parent.getIconPath(size);
	}

	@Override
	public boolean containsCustomValue(String key) {
		return parent.containsCustomValue(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return parent.getCustomValue(key);
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return parent.getCustomValues();
	}

	@Override
	public int getSchemaVersion() {
		return Integer.MAX_VALUE;
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
	public Collection<String> getMixinConfigs(EnvType type) {
		return Collections.emptyList();
	}

	@Override
	public String getAccessWidener() {
		return null;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return true;
	}

	@Override
	public Collection<String> getOldInitializers() {
		return Collections.emptyList();
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
	public void emitFormatWarnings() { }
}
