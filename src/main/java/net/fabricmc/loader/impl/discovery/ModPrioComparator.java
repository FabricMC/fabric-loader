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

package net.fabricmc.loader.impl.discovery;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.Version;

final class ModPrioComparator implements Comparator<ModCandidate> {
	private final Map<String, GroupIdContainer> groupIds; // for resolving the effective id used by sorting, this id applies consistently to all mods that may share an id through provides

	ModPrioComparator(Collection<ModCandidate> candidates) {
		this.groupIds = computeGroupIds(candidates);
	}

	private static Map<String, GroupIdContainer> computeGroupIds(Collection<ModCandidate> mods) {
		Map<String, GroupIdContainer> ret = new HashMap<>(mods.size());

		for (ModCandidate mod : mods) {
			if (mod.getProvides().isEmpty()) continue;

			// find existing id entry

			GroupIdContainer groupId = ret.get(mod.getId());

			if (groupId == null) {
				for (String provId : mod.getProvides()) {
					groupId = ret.get(provId);
					if (groupId != null) break;
				}
			}

			// update id entry to least id

			if (groupId == null) {
				groupId = new GroupIdContainer(mod.getId());
			} else {
				groupId.update(mod.getId());
			}

			ret.putIfAbsent(mod.getId(), groupId);

			for (String provId : mod.getProvides()) {
				ret.putIfAbsent(provId, groupId);
				groupId.update(provId);
			}
		}

		return ret;
	}

	private static final class GroupIdContainer {
		String minId;

		GroupIdContainer(String minId) {
			this.minId = minId;
		}

		void update(String id) {
			if (id.compareTo(minId) < 0) {
				minId = id;
			}
		}

		@Override
		public String toString() {
			return minId;
		}
	}

	@Override
	public int compare(ModCandidate a, ModCandidate b) {
		// descending sort prio (less/earlier is higher prio):
		// root mods first, lower id first, higher version first, less nesting first, parent cmp
		// if the ids are different the id+version comparison will consider all id+version pairs from each mod and its provides
		//   multiple overlaps with provides use a comparison counter to give the mod with the most version advantages priority
		// if the ids are equal the mod versions are assumed to represent desired priorities even if provided versions disagree
		// the id used for sorting is the least id used by the mod and its related provided set, including provides by other mods sharing a related id

		if (a.isRoot()) {
			if (!b.isRoot()) {
				return -1; // only a is root
			}
		} else if (b.isRoot()) {
			return 1; // only b is root
		}

		// sort id desc and version desc

		int idCmp = a.getId().compareTo(b.getId());

		if (idCmp == 0) { // same id
			// sort version desc (lower version later)
			int versionCmp = b.getVersion().compareTo(a.getVersion());
			if (versionCmp != 0) return versionCmp;
		} else if (groupIds.isEmpty()) { // different id without group ids
			return idCmp;
		} else { // different id
			// resolve group ids and thus effective mod ids for the next sorting step, required to reliably sort within groups
			GroupIdContainer groupIdA = groupIds.get(a.getId());
			GroupIdContainer groupIdB = groupIds.get(b.getId());
			String effectiveIdA = groupIdA != null ? groupIdA.minId : a.getId();
			String effectiveIdB = groupIdB != null ? groupIdB.minId : b.getId();

			int effectiveIdCmp = effectiveIdA.compareTo(effectiveIdB);
			if (effectiveIdCmp == 0) effectiveIdCmp = idCmp; // fall back to actual id comparison if same effective id

			if (groupIdA == null || groupIdB == null // mods with overlapping ids would have group ids for both a+b
					|| a.getProvides().isEmpty() && b.getProvides().isEmpty()) { // recorded overlaps but only for sibling mods with the same id but not the current a+b
				// certainly no overlapping ids, use effective id comparison
				return effectiveIdCmp;
			} else { // potentially overlapping ids
				int cmp = compareOverlappingIds(a, b, effectiveIdCmp);
				if (cmp != 0) return cmp;
			}
		}

		// sort nestLevel asc
		int nestCmp = a.getMinNestLevel() - b.getMinNestLevel(); // >0 if nest(a) > nest(b)
		if (nestCmp != 0) return nestCmp;

		if (a.isRoot()) return 0; // both root

		// find highest priority parent, if it is not shared by both a+b the one that has it is deemed higher prio
		return compareParents(a, b);
	}

	private static int compareOverlappingIds(ModCandidate a, ModCandidate b, int idCmp) {
		assert !a.getId().equals(b.getId()); // should have been handled before
		assert idCmp != 0;

		int ret = 0; // sum of individual normalized pair comparisons, may cancel each other out
		boolean matched = false; // whether any ids overlap, for falling back to main id comparison as if there were no provides

		for (String provIdA : a.getProvides()) { // a-provides vs b
			if (provIdA.equals(b.getId())) {
				Version providedVersionA = a.getVersion();
				ret += Integer.signum(b.getVersion().compareTo(providedVersionA));
				matched = true;
			}
		}

		for (String provIdB : b.getProvides()) {
			if (provIdB.equals(a.getId())) { // a vs b-provides
				Version providedVersionB = b.getVersion();
				ret += Integer.signum(providedVersionB.compareTo(a.getVersion()));
				matched = true;

				continue;
			}

			for (String provIdA : a.getProvides()) { // a-provides vs b-provides
				if (provIdB.equals(provIdA)) {
					Version providedVersionA = a.getVersion();
					Version providedVersionB = b.getVersion();

					ret += Integer.signum(providedVersionB.compareTo(providedVersionA));
					matched = true;

					break;
				}
			}
		}

		return matched ? ret : idCmp; // idCmp is != 0, so no need to compare version
	}

	private int compareParents(ModCandidate a, ModCandidate b) {
		assert !a.getParentMods().isEmpty() && !b.getParentMods().isEmpty();

		ModCandidate minParent = null;

		for (ModCandidate mod : a.getParentMods()) {
			if (minParent == null || mod != minParent && compare(minParent, mod) > 0) {
				minParent = mod;
			}
		}

		assert minParent != null;
		boolean found = false;

		for (ModCandidate mod : b.getParentMods()) {
			if (mod == minParent) { // both a and b have minParent
				found = true;
			} else if (compare(minParent, mod) > 0) { // b has a higher prio parent than a
				return 1;
			}
		}

		return found ? 0 : -1; // only a has minParent if !found, so only a has the highest prio parent
	}
}
