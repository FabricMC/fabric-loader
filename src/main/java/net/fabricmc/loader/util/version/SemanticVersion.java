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

import java.util.Arrays;
import java.util.Objects;

public class SemanticVersion implements Comparable<SemanticVersion>, Version {
	private final int[] components;
	private final String prerelease;
	private final String build;
	private String friendlyName;

	public SemanticVersion(String version, boolean storeX) throws VersionParsingException {
		int buildDelimPos = version.indexOf('+');
		if (buildDelimPos >= 0) {
			build = version.substring(buildDelimPos + 1);
			version = version.substring(0, buildDelimPos);
		} else {
			build = null;
		}

		int dashDelimPos = version.indexOf('-');
		if (dashDelimPos >= 0) {
			prerelease = version.substring(dashDelimPos + 1);
			version = version.substring(0, dashDelimPos);
		} else {
			prerelease = null;
		}

		if (version.endsWith(".")) {
			throw new VersionParsingException("Negative version number component found!");
		} else if (version.startsWith(".")) {
			throw new VersionParsingException("Missing version component!");
		}

		String[] componentStrings = version.split("\\.");
		if (componentStrings.length < 1) {
			throw new VersionParsingException("Did not provide version numbers!");
		}
		components = new int[componentStrings.length];
		for (int i = 0; i < componentStrings.length; i++) {
			if (storeX && componentStrings[i].equals("x")) {
				components[i] = Integer.MIN_VALUE;
				continue;
			}

			if (componentStrings[i].trim().isEmpty()) {
				throw new VersionParsingException("Missing version number component!");
			}

			try {
				components[i] = Integer.parseInt(componentStrings[i]);
				if (components[i] < 0) {
					throw new VersionParsingException("Negative version number component '" + componentStrings[i] + "'!");
				}
			} catch (NumberFormatException e) {
				throw new VersionParsingException("Could not parse version number component '" + componentStrings[i] + "'!", e);
			}
		}

		buildFriendlyName();
	}

	private void buildFriendlyName() {
		StringBuilder fnBuilder = new StringBuilder();
		boolean first = true;

		for (int i : components) {
			if (first) {
				first = false;
			} else {
				fnBuilder.append('.');
			}

			if (i == Integer.MIN_VALUE) {
				fnBuilder.append('x');
			} else {
				fnBuilder.append(i);
			}
		}

		if (prerelease != null) {
			fnBuilder.append('-').append(prerelease);
		}

		if (build != null) {
			fnBuilder.append('+').append(build);
		}

		friendlyName = fnBuilder.toString();
	}

	int getComponentLength() {
		return components.length;
	}

	int getComponent(int pos) {
		if (pos < 0) {
			throw new RuntimeException("Tried to access negative version number component!");
		} else if (pos >= components.length) {
			// Repeat "x" if x-range, otherwise repeat "0".
			return components[components.length - 1] == Integer.MIN_VALUE ? Integer.MIN_VALUE : 0;
		} else {
			return components[pos];
		}
	}

	boolean isPrerelease() {
		return prerelease != null;
	}

	@Override
	public String getFriendlyString() {
		return friendlyName;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SemanticVersion)) {
			return false;
		} else {
			SemanticVersion other = (SemanticVersion) o;
			if (!equalsComponentsExactly(other)) {
				return false;
			}

			return Objects.equals(prerelease, other.prerelease) && Objects.equals(build, other.build);
		}
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(components) * 73 + (prerelease != null ? prerelease.hashCode() * 11 : 0) + (build != null ? build.hashCode() : 0);
	}

	@Override
	public int compareTo(SemanticVersion o) {
		for (int i = 0; i < Math.max(getComponentLength(), o.getComponentLength()); i++) {
			int first = getComponent(i);
			int second = o.getComponent(i);
			if (first == Integer.MIN_VALUE || second == Integer.MIN_VALUE) {
				continue;
			}

			int compare = Integer.compare(first, second);
			if (compare != 0) {
				return compare;
			}
		}

		if (isPrerelease() || o.isPrerelease()) {
			if (isPrerelease() && o.isPrerelease()) {
				return prerelease.compareTo(o.prerelease);
			} else {
				return isPrerelease() ? -1 : 1;
			}
		} else {
			return 0;
		}
	}

	public boolean hasXRanges() {
		for (int i : components) {
			if (i < 0) {
				return true;
			}
		}

		return false;
	}

	public boolean equalsComponentsExactly(SemanticVersion other) {
		for (int i = 0; i < Math.max(getComponentLength(), other.getComponentLength()); i++) {
			if (getComponent(i) != other.getComponent(i)) {
				return false;
			}
		}

		return true;
	}
}
