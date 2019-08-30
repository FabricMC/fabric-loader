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

package net.fabricmc.loader.api;

import net.fabricmc.loader.util.version.VersionDeserializer;
import net.fabricmc.loader.util.version.VersionParsingException;

import java.util.Optional;

public interface SemanticVersion extends Version, Comparable<SemanticVersion> {
	int COMPONENT_WILDCARD = Integer.MIN_VALUE;

	int getVersionComponentCount();
	int getVersionComponent(int pos);

	Optional<String> getPrereleaseKey();
	Optional<String> getBuildKey();
	boolean hasWildcard();

	@Override
	default int compareTo(SemanticVersion o) {
		for (int i = 0; i < Math.max(getVersionComponentCount(), o.getVersionComponentCount()); i++) {
			int first = getVersionComponent(i);
			int second = o.getVersionComponent(i);
			if (first == COMPONENT_WILDCARD || second == COMPONENT_WILDCARD) {
				continue;
			}

			int compare = Integer.compare(first, second);
			if (compare != 0) {
				return compare;
			}
		}

		Optional<String> prereleaseA = getPrereleaseKey();
		Optional<String> prereleaseB = o.getPrereleaseKey();

		if (prereleaseA.isPresent() || prereleaseB.isPresent()) {
			if (prereleaseA.isPresent() && prereleaseB.isPresent()) {
				return prereleaseA.get().compareTo(prereleaseB.get());
			} else if (prereleaseA.isPresent()) {
				return o.hasWildcard() ? 0 : -1;
			} else { // prereleaseB.isPresent()
				return hasWildcard() ? 0 : 1;
			}
		} else {
			return 0;
		}
	}

	static SemanticVersion parse(String s) throws VersionParsingException {
		return VersionDeserializer.deserializeSemantic(s);
	}
}
