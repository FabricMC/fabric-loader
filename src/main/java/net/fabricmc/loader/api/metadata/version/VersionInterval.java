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

import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.util.version.VersionIntervalImpl;

/**
 * Representation of a version interval, closed or open.
 *
 * <p>The represented version interval is contiguous between its lower and upper limit, disjoint intervals are built
 * using collections of {@link VersionInterval}. Empty intervals may be represented by {@code null} or any interval
 * @code (x,x)} with x being a non-{@code null} version and both endpoints being exclusive.
 */
public interface VersionInterval {
	/**
	 * Get whether the interval uses {@link SemanticVersion} compatible bounds.
	 *
	 * @return True if both bounds are open (null), {@link SemanticVersion} instances or a combination of both, false otherwise.
	 */
	boolean isSemantic();

	/**
	 * Get the lower limit of the version interval.
	 *
	 * @return Version's lower limit or null if none, inclusive depending on {@link #isMinInclusive()}
	 */
	Version getMin();

	/**
	 * Get whether the lower limit of the version interval is inclusive.
	 *
	 * @return True if inclusive, false otherwise
	 */
	boolean isMinInclusive();

	/**
	 * Get the upper limit of the version interval.
	 *
	 * @return Version's upper limit or null if none, inclusive depending on {@link #isMaxInclusive()}
	 */
	Version getMax();

	/**
	 * Get whether the upper limit of the version interval is inclusive.
	 *
	 * @return True if inclusive, false otherwise
	 */
	boolean isMaxInclusive();

	default VersionInterval and(VersionInterval o) {
		return and(this, o);
	}

	default List<VersionInterval> or(Collection<VersionInterval> o) {
		return or(o, this);
	}

	/**
	 * Compute the intersection between two version intervals.
	 */
	static VersionInterval and(VersionInterval a, VersionInterval b) {
		return VersionIntervalImpl.and(a, b);
	}

	/**
	 * Compute the union between multiple version intervals.
	 */
	static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
		return VersionIntervalImpl.or(a, b);
	}
}
