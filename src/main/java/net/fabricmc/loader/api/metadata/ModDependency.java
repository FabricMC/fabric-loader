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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.discovery.DomainObject;

/**
 * Represents a dependency.
 */
public interface ModDependency {
	/**
	 * Get the kind of dependency.
	 */
	Kind getKind();

	/**
	 * Returns the ID of the mod to check.
	 */
	String getModId();

	/**
	 * Returns if the version fulfills this dependency's version requirement.
	 *
	 * @param version the version to check
	 */
	boolean matches(Version version);

	/**
	 * Returns if the dependency is active.
	 * If not, this dependency should be ignored.
	 * Must be called after {@link #resolveDependencyConditions(Map, EnvType)}.
	 */
	boolean isActive();

	/**
	 * If the dependency kind is CONDITION, this method will resolve its matchers (dependencies) and environment.
	 * If all the matchers are met, the dependency is considered active.
	 * <br>
	 * If the dependency kind is not CONDITION, this method will collect all active CONDITION dependencies' matchers.
	 * These matchers will be added to the list of ranges.
	 * If the ranges list is empty, the dependency is considered not active.
	 * <br>
	 * <b>Be aware that this method modifies the list of ranges, and must be called before {@link #isActive()}.</b>
	 * @param allMods all mods in the environment
	 */
	void resolveDependencyConditions(Map<String, List<DomainObject.Mod>> allMods, EnvType envType) throws VersionParsingException;

	/**
	 * Returns a representation of the dependency's version requirements.
	 *
	 * @return representation of the dependency's version requirements
	 */
	Collection<VersionPredicate> getVersionRequirements();

	/**
	 * Returns the version intervals covered by the dependency's version requirements.
	 *
	 * <p>There may be multiple because the allowed range may not be consecutive.
	 */
	List<VersionInterval> getVersionIntervals();

	/**
	 * @return the dependency's version requirements in string list form.
	 */
	List<String> getMatcherStrings();

	enum Kind {
		DEPENDS("depends", true, false),
		RECOMMENDS("recommends", true, true),
		SUGGESTS("suggests", true, true),
		CONFLICTS("conflicts", false, true),
		BREAKS("breaks", false, false),
		CONDITION("conditional", true, false);

		private static final Map<String, Kind> map = createMap();
		private final String key;
		private final boolean positive;
		private final boolean soft;

		Kind(String key, boolean positive, boolean soft) {
			this.key = key;
			this.positive = positive;
			this.soft = soft;
		}

		/**
		 * Get the key for the dependency as used by fabric.mod.json (v1+) and dependency overrides.
		 */
		public String getKey() {
			return key;
		}

		/**
		 * Get whether the dependency is positive, encouraging the inclusion of a mod instead of negative/discouraging.
		 */
		public boolean isPositive() {
			return positive;
		}

		/**
		 * Get whether it is a soft dependency, allowing the mod to still load if the dependency is unmet.
		 */
		public boolean isSoft() {
			return soft;
		}

		/**
		 * Parse a dependency kind from its key as provided by {@link #getKey}.
		 */
		public static Kind parse(String key) {
			return map.get(key);
		}

		private static Map<String, Kind> createMap() {
			Kind[] values = values();
			Map<String, Kind> ret = new HashMap<>(values.length);

			for (Kind kind : values) {
				ret.put(kind.key, kind);
			}

			return ret;
		}
	}
}
