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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class VersionPredicate {
	public enum Type {
		ANY("*"),
		EQUALS("="),
		GREATER_THAN(">"),
		LESSER_THAN("<"),
		GREATER_THAN_OR_EQUAL(">="),
		LESSER_THAN_OR_EQUAL("<="),
		SAME_MAJOR("^"),
		SAME_MAJOR_AND_MINOR("~");

		private final String prefix;

		Type(String prefix) {
			this.prefix = prefix;
		}

		public String prefix() {
			return prefix;
		}

		public String describe(String version) {
			return this == ANY ? prefix : prefix + version;
		}
	}

	private final Type type;
	private final String version;

	public VersionPredicate(Type type, String version) {
		this.type = Objects.requireNonNull(type, "type == null!");
		this.version = Objects.requireNonNull(version, "version == null!");
	}

	public Type getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return type.describe(version);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		VersionPredicate that = (VersionPredicate) o;
		return type == that.type && version.equals(that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, version);
	}

	public static Set<VersionPredicate> parse(Collection<String> matchers) {
		Set<VersionPredicate> predicates = new HashSet<>(matchers.size());

		for (String matcher : matchers) {
			char firstChar = matcher.charAt(0);
			char secondChar = 0;

			if (matcher.length() > 1) {
				secondChar = matcher.charAt(1);
			}

			switch (firstChar) {
			case '*':
				if (matcher.length() == 1) {
					return Collections.singleton(new VersionPredicate(Type.ANY, ""));
				} else {
					predicates.add(new VersionPredicate(Type.EQUALS, matcher));
				}

				break;
			case '>':
				if (secondChar == '=') {
					predicates.add(new VersionPredicate(Type.GREATER_THAN_OR_EQUAL, matcher.substring(2)));
				} else {
					predicates.add(new VersionPredicate(Type.GREATER_THAN, matcher.substring(1)));
				}

				break;
			case '<':
				if (secondChar == '=') {
					predicates.add(new VersionPredicate(Type.LESSER_THAN_OR_EQUAL, matcher.substring(2)));
				} else {
					predicates.add(new VersionPredicate(Type.LESSER_THAN, matcher.substring(1)));
				}

				break;
			case '=':
				predicates.add(new VersionPredicate(Type.EQUALS, matcher.substring(1)));
				break;
			case '^':
				predicates.add(new VersionPredicate(Type.SAME_MAJOR, matcher.substring(1)));
				break;
			case '~':
				predicates.add(new VersionPredicate(Type.SAME_MAJOR_AND_MINOR, matcher.substring(1)));
				break;
			default: // string version
				predicates.add(new VersionPredicate(Type.EQUALS, matcher));
				break;
			}
		}

		predicates = Collections.unmodifiableSet(predicates);
		return predicates;
	}
}
