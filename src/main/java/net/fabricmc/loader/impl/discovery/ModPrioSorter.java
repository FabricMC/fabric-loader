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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.Version;

final class ModPrioSorter {
	/**
	 * Sort the mod candidate list by priority.
	 *
	 * <p>This is implemented with two sorting passes, first sorting by isRoot/id/version/nesting/parent, then a best
	 * effort pass to prioritize mods that have overall newer id:version pairs.
	 *
	 * <p>The second pass won't prioritize non-root mods over root mods or above a mod with the same main id but a newer
	 * version as these cases are deemed deliberately influenced by the end user or mod author. Since there may be
	 * multiple id:version pairs the choice can only be best effort, but the SAT solver will ensure all hard constraints
	 * are still met later on.
	 *
	 * @param mods mods to sort
	 * @param modsById grouped mods output
	 */
	static void sort(List<ModCandidateImpl> mods, Map<String, List<ModCandidateImpl>> modsById) {
		// sort all mods by priority

		mods.sort(comparator);

		// group/index all mods by id, gather provided mod ids

		Set<String> providedMods = new HashSet<>();

		for (ModCandidateImpl mod : mods) {
			modsById.computeIfAbsent(mod.getId(), ignore -> new ArrayList<>()).add(mod);

			for (String provided : mod.getProvides()) {
				modsById.computeIfAbsent(provided, ignore -> new ArrayList<>()).add(mod);
				providedMods.add(provided);
			}
		}

		// strip any provided mod ids that don't have any effect (only 1 candidate for the id)

		for (Iterator<String> it = providedMods.iterator(); it.hasNext(); ) {
			if (modsById.get(it.next()).size() <= 1) {
				it.remove();
			}
		}

		// handle overlapping mod ids that need higher priority than the standard comparator handles
		// this is implemented through insertion sort which allows for skipping over unrelated mods that aren't properly comparable

		if (providedMods.isEmpty()) return; // no overlapping id mods

		// float overlapping ids up as needed

		boolean movedPastRoots = false;
		int startIdx = 0;
		Set<String> potentiallyOverlappingIds = new HashSet<>();

		for (int i = 0, size = mods.size(); i < size; i++) {
			ModCandidateImpl mod = mods.get(i);
			String id = mod.getId();

			//System.out.printf("%d: %s%n", i, mod);

			if (!movedPastRoots && !mod.isRoot()) { // update start index to avoid mixing root and non-root mods (root always has higher prio)
				movedPastRoots = true;
				startIdx = i;
			}

			// gather ids for mod that might overlap other mods

			if (providedMods.contains(id)) {
				potentiallyOverlappingIds.add(id);
			}

			if (!mod.getProvides().isEmpty()) {
				for (String provId : mod.getProvides()) {
					if (providedMods.contains(provId)) {
						potentiallyOverlappingIds.add(provId);
					}
				}
			}

			if (potentiallyOverlappingIds.isEmpty()) continue;

			// search for a suitable mod that overlaps mod but has a lower version

			int earliestIdx = -1;

			for (int j = i - 1; j >= startIdx; j--) {
				ModCandidateImpl cmpMod = mods.get(j);
				String cmpId = cmpMod.getId();
				if (cmpId.equals(id)) break; // can't move mod past another mod with the same id since that mod since that mod would have a higher version due to the previous sorting step and thus always has higher prio

				// quick check if it might match
				if (!potentiallyOverlappingIds.contains(cmpId)
						&& (cmpMod.getProvides().isEmpty() || Collections.disjoint(potentiallyOverlappingIds, cmpMod.getProvides()))) {
					continue;
				}

				int cmp = compareOverlappingIds(mod, cmpMod, Integer.MAX_VALUE);

				if (cmp < 0) { // mod needs to be after cmpMod, move mod forward
					//System.out.printf("found candidate for %d at %d (before %s)%n", i, j, cmpMod);
					earliestIdx = j;
				} else if (cmp != Integer.MAX_VALUE) { // cmpMod has at least the same prio, don't search past it
					break;
				}
			}

			if (earliestIdx >= 0) {
				//System.out.printf("move %d to %d (before %s)%n", i, earliestIdx, mods.get(earliestIdx));
				mods.remove(i);
				mods.add(earliestIdx, mod);
			}

			potentiallyOverlappingIds.clear();
		}
	}

	private static final Comparator<ModCandidateImpl> comparator = new Comparator<ModCandidateImpl>() {
		@Override
		public int compare(ModCandidateImpl a, ModCandidateImpl b) {
			return ModPrioSorter.compare(a, b);
		}
	};

	private static int compare(ModCandidateImpl a, ModCandidateImpl b) {
		// descending sort prio (less/earlier is higher prio):
		// root mods first, lower id first, higher version first, less nesting first, parent cmp

		if (a.isRoot()) {
			if (!b.isRoot()) {
				return -1; // only a is root
			}
		} else if (b.isRoot()) {
			return 1; // only b is root
		}

		// sort id asc

		int idCmp = a.getId().compareTo(b.getId());
		if (idCmp != 0) return idCmp;

		// sort version desc (lower version later)
		int versionCmp = b.getVersion().compareTo(a.getVersion());
		if (versionCmp != 0) return versionCmp;

		// sort nestLevel asc
		int nestCmp = a.getMinNestLevel() - b.getMinNestLevel(); // >0 if nest(a) > nest(b)
		if (nestCmp != 0) return nestCmp;

		if (a.isRoot()) return 0; // both root

		// find highest priority parent, if it is not shared by both a+b the one that has it is deemed higher prio
		return compareParents(a, b);
	}

	private static int compareParents(ModCandidateImpl a, ModCandidateImpl b) {
		assert !a.getParentMods().isEmpty() && !b.getParentMods().isEmpty();

		ModCandidateImpl minParent = null;

		for (ModCandidateImpl mod : a.getParentMods()) {
			if (minParent == null || mod != minParent && compare(minParent, mod) > 0) {
				minParent = mod;
			}
		}

		assert minParent != null;
		boolean found = false;

		for (ModCandidateImpl mod : b.getParentMods()) {
			if (mod == minParent) { // both a and b have minParent
				found = true;
			} else if (compare(minParent, mod) > 0) { // b has a higher prio parent than a
				return 1;
			}
		}

		return found ? 0 : -1; // only a has minParent if !found, so only a has the highest prio parent
	}

	private static int compareOverlappingIds(ModCandidateImpl a, ModCandidateImpl b, int noMatchResult) {
		assert !a.getId().equals(b.getId()); // should have been handled before

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

		return matched ? ret : noMatchResult;
	}
}
