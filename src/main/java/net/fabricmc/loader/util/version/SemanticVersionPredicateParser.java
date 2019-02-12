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

package net.fabricmc.loader.util.version;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SemanticVersionPredicateParser {
	private static final Map<String, Function<SemanticVersion, Predicate<SemanticVersion>>> PREFIXES;

	public static Predicate<SemanticVersion> create(String text) throws VersionParsingException {
		List<Predicate<SemanticVersion>> predicateList = new ArrayList<>();
		List<SemanticVersion> prereleaseVersions = new ArrayList<>();

		for (String s : text.split(" ")) {
			s = s.trim();
			if (s.isEmpty() || s.equals("*")) {
				continue;
			}

			Function<SemanticVersion, Predicate<SemanticVersion>> factory = null;
			for (String prefix : PREFIXES.keySet()) {
				if (s.startsWith(prefix)) {
					factory = PREFIXES.get(prefix);
					s = s.substring(prefix.length());
					break;
				}
			}

			SemanticVersion version = new SemanticVersion(s, true);
			if (version.isPrerelease()) {
				if (version.hasXRanges()) {
					throw new VersionParsingException("Pre-release versions are not allowed to use X-ranges!");
				}

				prereleaseVersions.add(version);
			}

			if (factory == null) {
				factory = PREFIXES.get("=");
			} else if (version.hasXRanges()) {
				throw new VersionParsingException("Prefixed ranges are not allowed to use X-ranges!");
			}

			predicateList.add(factory.apply(version));
		}

		switch (predicateList.size()) {
			case 0:
				return (s) -> true;
			default:
				return (s) -> {
					if (s.isPrerelease()) {
						boolean match = false;
						for (SemanticVersion version : prereleaseVersions) {
							if (version.equalsComponentsExactly(s)) {
								match = true;
								break;
							}
						}

						if (!match) {
							return false;
						}
					}

					for (Predicate<SemanticVersion> p : predicateList) {
						if (!p.test(s)) {
							return false;
						}
					}

					return true;
				};
		}
	}

	private static int length(SemanticVersion a, SemanticVersion b) {
		return Math.min(a.getComponentLength(), b.getComponentLength());
	}

	static {
		// Make sure to keep this sorted in order of length!
		PREFIXES = new LinkedHashMap<>();
		PREFIXES.put(">=", (target) -> (source) -> source.compareTo(target) >= 0);
		PREFIXES.put("<=", (target) -> (source) -> source.compareTo(target) <= 0);
		PREFIXES.put(">", (target) -> (source) -> source.compareTo(target) > 0);
		PREFIXES.put("<", (target) -> (source) -> source.compareTo(target) < 0);
		PREFIXES.put("=", (target) -> (source) -> source.compareTo(target) == 0);
		PREFIXES.put("~", (target) -> (source) -> {
			if (target.getComponentLength() == 1) {
				if (target.isPrerelease()) {
					throw new RuntimeException("Unsupported condition!");
				}

				return source.getComponent(0) == target.getComponent(0);
			} else {
				return source.compareTo(target) >= 0
					&& source.getComponent(0) == target.getComponent(0)
					&& source.getComponent(1) == target.getComponent(1);
			}
		});
		PREFIXES.put("^", (target) -> (source) -> {
			if (source.compareTo(target) < 0) {
				return false;
			}

			for (int i = 0; i < target.getComponentLength(); i++) {
				if (target.getComponent(i) != 0) {
					return source.getComponent(i) == target.getComponent(i);
				}
			}

			throw new RuntimeException("Unsupported condition!");
		});
	}
}
