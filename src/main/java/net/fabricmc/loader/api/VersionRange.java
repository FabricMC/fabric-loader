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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

public class VersionRange {
	public enum Type {
		INVALID,
		ANY,
		EQUALS,
		GREATER_THAN,
		LESSER_THAN,
		GREATER_THAN_OR_EQUAL,
		LESSER_THAN_OR_EQUAL,
		SAME_MAJOR,
		SAME_MAJOR_AND_MINOR;

		public String represent(String version) {
			switch (this) {
			case INVALID:
				return "unknown version";
			case ANY:
				return "any version";
			case EQUALS:
				return "version " + version;
			case GREATER_THAN:
				return "any version after " + version;
			case LESSER_THAN:
				return "any version before " + version;
			case GREATER_THAN_OR_EQUAL:
				return "version " + version + " or later";
			case LESSER_THAN_OR_EQUAL:
				return "version " + version + " or earlier";
			case SAME_MAJOR:
			case SAME_MAJOR_AND_MINOR:
				String[] parts = version.split("\\.");
				int start = 1;

				if (this == Type.SAME_MAJOR_AND_MINOR) {
					start = 2;
				}

				for (int i = start; i < parts.length; i++) {
					parts[i] = "x";
				}

				return "version " + String.join(".", parts);
			default:
				return "unhandled version range type " + this;
			}
		}
	}

	private final Type type;
	private final String version;
	private String stringRepr;

	public VersionRange(Type type, String version) {
		this.type = type;
		this.version = version;
	}

	public Type getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		if (stringRepr == null) {
			stringRepr = type.represent(version);
		}

		return stringRepr;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		VersionRange that = (VersionRange) o;
		return type == that.type && version.equals(that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, version);
	}

	public static Collection<VersionRange> fromVersionMatchers(String... matchers) {
		Collection<VersionRange> ranges = Arrays.stream(matchers).map(matcher -> {
			char firstChar = matcher.charAt(0);
			char secondChar = 0;

			if (matcher.length() > 1) {
				secondChar = matcher.charAt(1);
			}

			switch (firstChar) {
			case '*':
				if (matcher.length() == 1) {
					return new VersionRange(Type.ANY, "");
				} else {
					return new VersionRange(Type.INVALID, "");
				}
			case '>':
				if (secondChar == '=') {
					return new VersionRange(Type.GREATER_THAN_OR_EQUAL, matcher.substring(2));
				} else {
					return new VersionRange(Type.GREATER_THAN, matcher.substring(1));
				}
			case '<':
				if (secondChar == '=') {
					return new VersionRange(Type.LESSER_THAN_OR_EQUAL, matcher.substring(2));
				} else {
					return new VersionRange(Type.LESSER_THAN, matcher.substring(1));
				}
			case '=':
				return new VersionRange(Type.EQUALS, matcher.substring(1));
			case '^':
				return new VersionRange(Type.SAME_MAJOR, matcher.substring(1));
			case '~':
				return new VersionRange(Type.SAME_MAJOR_AND_MINOR, matcher.substring(1));
			default: // string version
				return new VersionRange(Type.EQUALS, matcher);
			}
		}).collect(Collectors.toSet());

		// simplify: if one ANY range exists, only use that
		if (ranges.stream().anyMatch(range -> range.getType() == Type.ANY)) {
			ranges.clear();
			ranges.add(new VersionRange(Type.ANY, ""));
		}

		ranges = Collections.unmodifiableCollection(ranges);
		return ranges;
	}
}
