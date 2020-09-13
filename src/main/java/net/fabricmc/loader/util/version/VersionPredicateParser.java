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

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.function.Predicate;

@FunctionalInterface
public interface VersionPredicateParser<E extends Version> {
	/**
	 * Parse and create a predicate comparing given Version objects.
	 *
	 * @param s The predicate string. Guaranteed to be non-null and non-empty.
	 * @return The resulting predicate.
	 */
	Predicate<E> create(String s);

	static boolean matches(Version version, String s) throws VersionParsingException {
		if (version instanceof SemanticVersionImpl) {
			return SemanticVersionPredicateParser.create(s).test((SemanticVersionImpl) version);
		} else if (version instanceof StringVersion) {
			return StringVersionPredicateParser.create(s).test((StringVersion) version);
		} else {
			throw new VersionParsingException("Unknown version type!");
		}
	}
}
