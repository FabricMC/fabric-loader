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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class VersionRange {
	public enum Type {
		INVALID(version -> "unknown version"),
		ANY(version -> "any version"),
		EQUALS(version -> "version " + version),
		GREATER_THAN(version -> "any version after " + version),
		LESSER_THAN(version -> "any version before " + version),
		GREATER_THAN_OR_EQUAL(version -> "version " + version + " or later"),
		LESSER_THAN_OR_EQUAL(version -> "version " + version + " or earlier"),
		SAME_MAJOR(version -> {
			String[] parts = version.split("\\.");

			for (int i = 1; i < parts.length; i++) {
				parts[i] = "x";
			}

			return "version " + String.join(".", parts);
		}),
		SAME_MAJOR_AND_MINOR(version -> {
			String[] parts = version.split("\\.");

			for (int i = 2; i < parts.length; i++) {
				parts[i] = "x";
			}

			return "version " + String.join(".", parts);
		});

		private final UnaryOperator<String> reprOperator;

		Type(UnaryOperator<String> reprOperator) {
			this.reprOperator = reprOperator;
		}

		public String represent(String version) {
			return reprOperator.apply(version);
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
