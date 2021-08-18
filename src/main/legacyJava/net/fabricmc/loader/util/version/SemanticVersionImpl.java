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

import java.util.Optional;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public class SemanticVersionImpl implements SemanticVersion {
	private final SemanticVersion parent;

	protected SemanticVersionImpl() {
		parent = null;
	}

	public SemanticVersionImpl(String version, boolean storeX) throws VersionParsingException {
		parent = SemanticVersion.parse(version);
	}

	@Override
	public int getVersionComponentCount() {
		return parent.getVersionComponentCount();
	}

	@Override
	public int getVersionComponent(int pos) {
		return parent.getVersionComponent(pos);
	}

	@Override
	public Optional<String> getPrereleaseKey() {
		return parent.getPrereleaseKey();
	}

	@Override
	public Optional<String> getBuildKey() {
		return parent.getBuildKey();
	}

	@Override
	public String getFriendlyString() {
		return parent.getFriendlyString();
	}

	@Override
	public boolean equals(Object o) {
		return parent.equals(o);
	}

	@Override
	public int hashCode() {
		return parent.hashCode();
	}

	@Override
	public String toString() {
		return parent.toString();
	}

	@Override
	public boolean hasWildcard() {
		return parent.hasWildcard();
	}

	public boolean equalsComponentsExactly(SemanticVersionImpl other) {
		for (int i = 0; i < Math.max(getVersionComponentCount(), other.getVersionComponentCount()); i++) {
			if (getVersionComponent(i) != other.getVersionComponent(i)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int compareTo(Version o) {
		return parent.compareTo(o);
	}
}
