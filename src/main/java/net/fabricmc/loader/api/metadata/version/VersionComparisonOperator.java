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

package net.fabricmc.loader.api.metadata.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;

public enum VersionComparisonOperator {
	// order is important to match the longest substring (e.g. try >= before >)
	GREATER_EQUAL(">=", true, false) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) >= 0;
		}

		@Override
		public SemanticVersion minVersion(SemanticVersion version) {
			return version;
		}
	},
	LESS_EQUAL("<=", false, true) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) <= 0;
		}

		@Override
		public SemanticVersion maxVersion(SemanticVersion version) {
			return version;
		}
	},
	GREATER(">", false, false) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) > 0;
		}

		@Override
		public SemanticVersion minVersion(SemanticVersion version) {
			return version;
		}
	},
	LESS("<", false, false) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) < 0;
		}

		@Override
		public SemanticVersion maxVersion(SemanticVersion version) {
			return version;
		}
	},
	EQUAL("=", true, true) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) == 0;
		}

		@Override
		public SemanticVersion minVersion(SemanticVersion version) {
			return version;
		}

		@Override
		public SemanticVersion maxVersion(SemanticVersion version) {
			return version;
		}
	},
	SAME_TO_NEXT_MINOR("~", true, false) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) >= 0
					&& a.getVersionComponent(0) == b.getVersionComponent(0)
					&& a.getVersionComponent(1) == b.getVersionComponent(1);
		}

		@Override
		public SemanticVersion minVersion(SemanticVersion version) {
			return version;
		}

		@Override
		public SemanticVersion maxVersion(SemanticVersion version) {
			return new SemanticVersionImpl(new int[] { version.getVersionComponent(0), version.getVersionComponent(1) + 1 }, "", null);
		}
	},
	SAME_TO_NEXT_MAJOR("^", true, false) {
		@Override
		public boolean test(SemanticVersion a, SemanticVersion b) {
			return a.compareTo((Version) b) >= 0
					&& a.getVersionComponent(0) == b.getVersionComponent(0);
		}

		@Override
		public SemanticVersion minVersion(SemanticVersion version) {
			return version;
		}

		@Override
		public SemanticVersion maxVersion(SemanticVersion version) {
			return new SemanticVersionImpl(new int[] { version.getVersionComponent(0) + 1 }, "", null);
		}
	};

	private final String serialized;
	private final boolean minInclusive;
	private final boolean maxInclusive;

	VersionComparisonOperator(String serialized, boolean minInclusive, boolean maxInclusive) {
		this.serialized = serialized;
		this.minInclusive = minInclusive;
		this.maxInclusive = maxInclusive;
	}

	public final String getSerialized() {
		return serialized;
	}

	public final boolean isMinInclusive() {
		return minInclusive;
	}

	public final boolean isMaxInclusive() {
		return maxInclusive;
	}

	public final boolean test(Version a, Version b) {
		if (a instanceof SemanticVersion && b instanceof SemanticVersion) {
			return test((SemanticVersion) a, (SemanticVersion) b);
		} else if (minInclusive || maxInclusive) {
			return a.getFriendlyString().equals(b.getFriendlyString());
		} else {
			return false;
		}
	}

	public abstract boolean test(SemanticVersion a, SemanticVersion b);

	public SemanticVersion minVersion(SemanticVersion version) {
		return null;
	}

	public SemanticVersion maxVersion(SemanticVersion version) {
		return null;
	}
}
