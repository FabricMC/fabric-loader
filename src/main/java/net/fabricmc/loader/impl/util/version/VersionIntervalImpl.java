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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

public final class VersionIntervalImpl implements VersionInterval {
	static final VersionInterval INFINITE = new VersionIntervalImpl(null, false, null, false);

	private final Version min;
	private final boolean minInclusive;
	private final Version max;
	private final boolean maxInclusive;

	VersionIntervalImpl(Version min, boolean minInclusive,
			Version max, boolean maxInclusive) {
		this.min = min;
		this.minInclusive = min != null ? minInclusive : false;
		this.max = max;
		this.maxInclusive = max != null ? maxInclusive : false;

		assert min != null || !minInclusive;
		assert max != null || !maxInclusive;
		assert min == null || min instanceof SemanticVersion || minInclusive;
		assert max == null || max instanceof SemanticVersion || maxInclusive;
		assert min == null || max == null || min instanceof SemanticVersion && max instanceof SemanticVersion || min.equals(max);
	}

	@Override
	public boolean isSemantic() {
		return (min == null || min instanceof SemanticVersion)
				&& (max == null || max instanceof SemanticVersion);
	}

	@Override
	public Version getMin() {
		return min;
	}

	@Override
	public boolean isMinInclusive() {
		return minInclusive;
	}

	@Override
	public Version getMax() {
		return max;
	}

	@Override
	public boolean isMaxInclusive() {
		return maxInclusive;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof VersionInterval) {
			VersionInterval o = (VersionInterval) obj;

			return Objects.equals(min, o.getMin()) && minInclusive == o.isMinInclusive()
					&& Objects.equals(max, o.getMax()) && maxInclusive == o.isMaxInclusive();
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		if (min == null) {
			if (max == null) {
				return "(-∞,∞)";
			} else {
				return String.format("(-∞,%s%c", max, maxInclusive ? ']' : ')');
			}
		} else if (max == null) {
			return String.format("%c%s,∞)", minInclusive ? '[' : '(', min);
		} else {
			return String.format("%c%s,%s%c", minInclusive ? '[' : '(', min, max, maxInclusive ? ']' : ')');
		}
	}

	public static VersionInterval and(VersionInterval a, VersionInterval b) {
		if (a == null || b == null) return null;

		if (!a.isSemantic() || !b.isSemantic()) {
			return andPlain(a, b);
		} else {
			return andSemantic(a, b);
		}
	}

	private static VersionInterval andPlain(VersionInterval a, VersionInterval b) {
		Version aMin = a.getMin();
		Version aMax = a.getMax();
		Version bMin = b.getMin();
		Version bMax = b.getMax();

		if (aMin != null) { // -> min must be aMin or invalid
			if (bMin != null && !aMin.equals(bMin) || bMax != null && !aMin.equals(bMax)) {
				return null;
			}

			if (aMax != null || bMax == null) {
				assert Objects.equals(aMax, bMax) || bMax == null;
				return a;
			} else {
				return new VersionIntervalImpl(aMin, true, bMax, b.isMaxInclusive());
			}
		} else if (aMax != null) { // -> min must be bMin, max must be aMax or invalid
			if (bMin != null && !aMax.equals(bMin) || bMax != null && !aMax.equals(bMax)) {
				return null;
			}

			if (bMin == null) {
				return a;
			} else if (bMax != null) {
				return b;
			} else {
				return new VersionIntervalImpl(bMin, true, aMax, true);
			}
		} else {
			return b;
		}
	}

	private static VersionInterval andSemantic(VersionInterval a, VersionInterval b) {
		int minCmp = compareMin(a, b);
		int maxCmp = compareMax(a, b);

		if (minCmp == 0) { // aMin == bMin
			if (maxCmp == 0) { // aMax == bMax -> a == b -> a/b
				return a;
			} else { // aMax != bMax -> a/b..min(a,b)
				return maxCmp < 0 ? a : b;
			}
		} else if (maxCmp == 0) { // aMax == bMax, aMin != bMin -> max(a,b)..a/b
			return minCmp < 0 ? b : a;
		} else if (minCmp < 0) { // aMin < bMin, aMax != bMax -> b..min(a,b)
			if (maxCmp > 0) return b; // a > b -> b

			SemanticVersion aMax = (SemanticVersion) a.getMax();
			SemanticVersion bMin = (SemanticVersion) b.getMin();
			int cmp = bMin.compareTo((Version) aMax);

			if (cmp < 0 || cmp == 0 && b.isMinInclusive() && a.isMaxInclusive()) {
				return new VersionIntervalImpl(bMin, b.isMinInclusive(), aMax, a.isMaxInclusive());
			} else {
				return null;
			}
		} else { // aMin > bMin, aMax != bMax -> a..min(a,b)
			if (maxCmp < 0) return a; // a < b -> a

			SemanticVersion aMin = (SemanticVersion) a.getMin();
			SemanticVersion bMax = (SemanticVersion) b.getMax();
			int cmp = aMin.compareTo((Version) bMax);

			if (cmp < 0 || cmp == 0 && a.isMinInclusive() && b.isMaxInclusive()) {
				return new VersionIntervalImpl(aMin, a.isMinInclusive(), bMax, b.isMaxInclusive());
			} else {
				return null;
			}
		}
	}

	public static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
		List<VersionInterval> ret = new ArrayList<>(a.size() + 1);

		for (VersionInterval v : a) {
			merge(v, ret);
		}

		merge(b, ret);

		return ret;
	}

	private static void merge(VersionInterval a, List<VersionInterval> out) {
		if (a == null) return;

		if (out.isEmpty()) {
			out.add(a);
			return;
		}

		if (out.size() == 1) {
			VersionInterval e = out.get(0);

			if (e.getMin() == null && e.getMax() == null) {
				return;
			}
		}

		if (!a.isSemantic()) {
			mergePlain(a, out);
		} else {
			mergeSemantic(a, out);
		}
	}

	private static void mergePlain(VersionInterval a, List<VersionInterval> out) {
		Version aMin = a.getMin();
		Version aMax = a.getMax();
		Version v = aMin != null ? aMin : aMax;
		assert v != null;

		for (int i = 0; i < out.size(); i++) {
			VersionInterval c = out.get(i);

			if (v.equals(c.getMin())) {
				if (aMin == null) {
					assert aMax.equals(c.getMin());
					out.clear();
					out.add(INFINITE);
				} else if (aMax == null && c.getMax() != null) {
					out.set(i, a);
				}

				return;
			} else if (v.equals(c.getMax())) {
				assert c.getMin() == null;

				if (aMax == null) {
					assert aMin.equals(c.getMax());
					out.clear();
					out.add(INFINITE);
				}

				return;
			}
		}

		out.add(a);
	}

	private static void mergeSemantic(VersionInterval a, List<VersionInterval> out) {
		SemanticVersion aMin = (SemanticVersion) a.getMin();
		SemanticVersion aMax = (SemanticVersion) a.getMax();

		if (aMin == null && aMax == null) {
			out.clear();
			out.add(INFINITE);
			return;
		}

		for (int i = 0; i < out.size(); i++) {
			VersionInterval c = out.get(i);
			if (!c.isSemantic()) continue;

			SemanticVersion cMin = (SemanticVersion) c.getMin();
			SemanticVersion cMax = (SemanticVersion) c.getMax();
			int cmp;

			if (aMin == null) { // ..a..]
				if (cMax == null) { // ..a..] [..c..
					cmp = aMax.compareTo((Version) cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c.. or ..a..)(..c..
						out.add(i, a);
					} else { // ..a..|..c.. or ..a.[..].c..
						out.clear();
						out.add(INFINITE);
					}

					return;
				} else { // ..a..] [..c..]
					cmp = compareMax(a, c);

					if (cmp >= 0) { // a encompasses c
						out.remove(i);
						i--;
					} else if (cMin == null) { // c encompasses a
						return;
					} else { // aMax < cMax
						cmp = aMax.compareTo((Version) cMin);

						if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c..] or ..a..)(..c..]
							out.add(i, a);
						} else { // c extends a to the right
							out.set(i, new VersionIntervalImpl(null, false, cMax, c.isMaxInclusive()));
						}

						return;
					}
				}
			} else if (cMax == null) { // [..c..
				cmp = compareMin(a, c);

				if (cmp >= 0) { // c encompasses a
					// no-op
				} else if (aMax == null) { // a encompasses c
					while (out.size() > i) out.remove(i);
					out.add(a);
				} else { // aMin < cMin
					cmp = aMax.compareTo((Version) cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // [..a..]..[..c.. or [..a..)(..c..
						out.add(i, a);
					} else { // a extends c to the left
						out.set(i, new VersionIntervalImpl(aMin, a.isMinInclusive(), null, false));
					}
				}

				return;
			} else if ((cmp = aMin.compareTo((Version) cMax)) < 0 || cmp == 0 && (a.isMinInclusive() || c.isMaxInclusive())) {
				int cmp2;

				if (aMax == null || cMin == null || (cmp2 = aMax.compareTo((Version) cMin)) > 0 || cmp2 == 0 && (a.isMaxInclusive() || c.isMinInclusive())) {
					int cmpMin = compareMin(a, c);
					int cmpMax = compareMax(a, c);

					if (cmpMax <= 0) { // aMax <= cMax
						if (cmpMin < 0) { // aMin < cMin
							out.set(i, new VersionIntervalImpl(aMin, a.isMinInclusive(), cMax, c.isMaxInclusive()));
						}

						return;
					} else if (cmpMin > 0) { // aMin > cMin, aMax > cMax
						a = new VersionIntervalImpl(cMin, c.isMinInclusive(), aMax, a.isMaxInclusive());
					}

					out.remove(i);
					i--;
				} else {
					out.add(i, a);
					return;
				}
			}
		}

		out.add(a);
	}

	private static int compareMin(VersionInterval a, VersionInterval b) {
		SemanticVersion aMin = (SemanticVersion) a.getMin();
		SemanticVersion bMin = (SemanticVersion) b.getMin();
		int cmp;

		if (aMin == null) { // a <= b
			if (bMin == null) { // a == b == -inf
				return 0;
			} else { // bMin != null -> a < b
				return -1;
			}
		} else if (bMin == null || (cmp = aMin.compareTo((Version) bMin)) > 0 || cmp == 0 && !a.isMinInclusive() && b.isMinInclusive()) { // a > b
			return 1;
		} else if (cmp < 0 || a.isMinInclusive() && !b.isMinInclusive()) { // a < b
			return -1;
		} else { // cmp == 0 && a.minInclusive() == b.minInclusive() -> a == b
			return 0;
		}
	}

	private static int compareMax(VersionInterval a, VersionInterval b) {
		SemanticVersion aMax = (SemanticVersion) a.getMax();
		SemanticVersion bMax = (SemanticVersion) b.getMax();
		int cmp;

		if (aMax == null) { // a >= b
			if (bMax == null) { // a == b == inf
				return 0;
			} else { // bMax != null -> a > b
				return 1;
			}
		} else if (bMax == null || (cmp = aMax.compareTo((Version) bMax)) < 0 || cmp == 0 && !a.isMaxInclusive() && b.isMaxInclusive()) { // a < b
			return -1;
		} else if (cmp > 0 || a.isMaxInclusive() && !b.isMaxInclusive()) { // a > b
			return 1;
		} else { // cmp == 0 && a.maxInclusive() == b.maxInclusive() -> a == b
			return 0;
		}
	}
}
