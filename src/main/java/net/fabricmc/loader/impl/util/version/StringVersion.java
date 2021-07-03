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

package net.fabricmc.loader.impl.util.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;

public class StringVersion implements Version {
	private final String version;

	public StringVersion(String version) {
		this.version = version;
	}

	@Override
	public String getFriendlyString() {
		return version;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StringVersion) {
			return version.equals(((StringVersion) obj).version);
		} else {
			return false;
		}
	}

	@Override
	public int compareTo(Version o) {
		if (o instanceof SemanticVersion) {
			return -1;
		}

		return getFriendlyString().compareTo(o.getFriendlyString());
	}

	@Override
	public String toString() {
		return version;
	}
}
