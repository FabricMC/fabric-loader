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

package net.fabricmc.loader.api.metadata;

import com.google.gson.JsonElement;
import net.fabricmc.loader.api.Version;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ModMetadata {
	String getType();

	// When adding getters, follow the order as presented on the wiki.
	// No defaults.

	String getId();
	Version getVersion();

	Collection<ModDependency> getDepends();
	Collection<ModDependency> getRecommends();
	Collection<ModDependency> getSuggests();
	Collection<ModDependency> getConflicts();
	Collection<ModDependency> getBreaks();

	String getName();
	String getDescription();
	Collection<Person> getAuthors();
	Collection<Person> getContributors();
	ContactInformation getContact();
	Collection<String> getLicense();

	/**
	 * Get the path to an icon.
	 *
	 * The standard defines icons as square .PNG files, however their
	 * dimensions are not defined - in particular, they are not
	 * guaranteed to be a power of two.
	 *
	 * The preferred size is used in the following manner:
	 * - the smallest image larger than or equal to the size
	 *   is returned, if one is present;
	 * - failing that, the largest image is returned.
	 *
	 * @param size The preferred size.
	 * @return The icon path, if any.
	 */
	Optional<String> getIconPath(int size);

	boolean containsCustomValue(String key);
	CustomValue getCustomValue(String key);
	Map<String, CustomValue> getCustomValues();

	/**
	 * @deprecated Use {@link #containsCustomValue} instead, this will be removed (can't expose GSON types)!
	 */
	@Deprecated
	boolean containsCustomElement(String key);

	/**
	 * @deprecated Use {@link #getCustomValue} instead, this will be removed (can't expose GSON types)!
	 */
	@Deprecated
	JsonElement getCustomElement(String key);
}
