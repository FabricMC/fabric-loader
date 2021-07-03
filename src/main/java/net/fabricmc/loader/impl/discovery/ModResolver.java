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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class ModResolver {
	public static List<ModCandidate> resolve(Collection<ModCandidate> candidates) throws ModResolutionException {
		long startTime = System.nanoTime();
		List<ModCandidate> result = findCompatibleSet(candidates);

		long endTime = System.nanoTime();
		Log.debug(LogCategory.RESOLUTION, "Mod resolution time: %.1f ms", (endTime - startTime) * 1e-6);

		return result;
	}

	private static List<ModCandidate> findCompatibleSet(Collection<ModCandidate> candidates) throws ModResolutionException {
		// gather all mods (root+nested)

		Set<ModCandidate> allMods = new HashSet<>();
		Queue<ModCandidate> queue = new ArrayDeque<>();

		{
			queue.addAll(candidates);
			ModCandidate mod;

			while ((mod = queue.poll()) != null) {
				if (allMods.add(mod)) {
					queue.addAll(mod.getNestedMods());
				}
			}
		}

		// sort all mods by priority

		List<ModCandidate> allModsSorted = new ArrayList<>(allMods);

		allModsSorted.sort(modPrioComparator);

		// group/index all mods by id

		Map<String, List<ModCandidate>> modsById = new LinkedHashMap<>(); // linked to ensure consistent execution

		for (ModCandidate mod : allModsSorted) {
			modsById.computeIfAbsent(mod.getId(), ignore -> new ArrayList<>()).add(mod);

			for (String provided : mod.getProvides()) {
				modsById.computeIfAbsent(provided, ignore -> new ArrayList<>()).add(mod);
			}
		}

		// preselect mods, check for builtin mod collisions

		List<ModCandidate> preselectedMods = new ArrayList<>();

		for (List<ModCandidate> mods : modsById.values()) {
			ModCandidate builtinMod = null;

			for (ModCandidate mod : mods) {
				if (mod.isBuiltin()) {
					builtinMod = mod;
					break;
				}
			}

			if (builtinMod == null) continue;

			if (mods.size() > 1) {
				mods.remove(builtinMod);
				throw new ModResolutionException("Mods share ID with builtin mod %s: %s", builtinMod, mods);
			}

			preselectedMods.add(builtinMod);
		}

		Map<String, ModCandidate> selectedMods = new HashMap<>(allModsSorted.size());
		List<ModCandidate> uniqueSelectedMods = new ArrayList<>(allModsSorted.size());

		for (ModCandidate mod : preselectedMods) {
			preselectMod(mod, allModsSorted, modsById, selectedMods, uniqueSelectedMods);
		}

		// solve

		ModSolver.Result result;

		try {
			result = ModSolver.solve(allModsSorted, modsById,
					selectedMods, uniqueSelectedMods);
		} catch (ContradictionException | TimeoutException e) {
			throw new ModResolutionException("Solving failed", e);
		}

		if (!result.success) {
			Log.warn(LogCategory.RESOLUTION, "Mod resolution failed");
			Log.info(LogCategory.RESOLUTION, "Immediate reason: %s%n", result.immediateReason);
			Log.info(LogCategory.RESOLUTION, "Reason: %s%n", result.reason);

			if (result.fix == null) {
				Log.info(LogCategory.RESOLUTION, "No fix?");
			} else {
				Log.info(LogCategory.RESOLUTION, "Fix: add %s, remove %s, replace [%s]%n",
						result.fix.modsToAdd,
						result.fix.modsToRemove,
						result.fix.modReplacements.entrySet().stream().map(e -> String.format("%s -> %s", e.getValue(), e.getKey())).collect(Collectors.joining(", ")));
			}

			throw new ModResolutionException("Errors were found!%s", ResultAnalyzer.gatherErrors(result, selectedMods, modsById));
		}

		uniqueSelectedMods.sort(Comparator.comparing(ModCandidate::getId));

		// clear cached data and inbound refs for unused mods, set minNestLevel for used non-root mods to max, queue root mods

		for (ModCandidate mod : allModsSorted) {
			if (selectedMods.get(mod.getId()) == mod) { // mod is selected
				if (!mod.resetMinNestLevel()) { // -> is root
					queue.add(mod);
				}
			} else {
				mod.clearCachedData();

				for (ModCandidate m : mod.getNestedMods()) {
					m.getParentMods().remove(mod);
				}

				for (ModCandidate m : mod.getParentMods()) {
					m.getNestedMods().remove(mod);
				}
			}
		}

		// recompute minNestLevel (may have changed due to parent associations having been dropped by the above step)

		{
			ModCandidate mod;

			while ((mod = queue.poll()) != null) {
				for (ModCandidate child : mod.getNestedMods()) {
					if (child.updateMinNestLevel(mod)) {
						queue.add(child);
					}
				}
			}
		}

		String warnings = ResultAnalyzer.gatherWarnings(uniqueSelectedMods, selectedMods);

		if (warnings != null) {
			Log.warn(LogCategory.RESOLUTION, "Warnings were found!%s", warnings);
		}

		return uniqueSelectedMods;
	}

	private static final Comparator<ModCandidate> modPrioComparator = new Comparator<ModCandidate>() {
		@Override
		public int compare(ModCandidate a, ModCandidate b) {
			// descending sort prio (less/earlier is higher prio):
			// root mods first, lower id first, higher version first, less nesting first, parent cmp

			if (a.isRoot()) {
				if (!b.isRoot()) {
					return -1; // only a is root
				}
			} else if (b.isRoot()) {
				return 1; // only b is root
			}

			// sort id desc
			int idCmp = a.getId().compareTo(b.getId());
			if (idCmp != 0) return idCmp;

			// sort version desc (lower version later)
			int versionCmp = b.getVersion().compareTo(a.getVersion());
			if (versionCmp != 0) return versionCmp;

			// sort nestLevel asc
			int nestCmp = a.getMinNestLevel() - b.getMinNestLevel(); // >0 if nest(a) > nest(b)
			if (nestCmp != 0) return nestCmp;

			if (a.isRoot()) return 0; // both root

			List<ModCandidate> parents = new ArrayList<>(a.getParentMods().size() + b.getParentMods().size());
			parents.addAll(a.getParentMods());
			parents.addAll(b.getParentMods());
			parents.sort(this);

			if (a.getParentMods().contains(parents.get(0))) {
				if (b.getParentMods().contains(parents.get(0))) {
					return 0;
				} else {
					return -1;
				}
			} else {
				return 1;
			}
		}
	};

	static void preselectMod(ModCandidate mod, List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<String, ModCandidate> selectedMods, List<ModCandidate> uniqueSelectedMods) throws ModResolutionException {
		selectMod(mod, selectedMods, uniqueSelectedMods);

		allModsSorted.removeAll(modsById.remove(mod.getId()));

		for (String provided : mod.getProvides()) {
			allModsSorted.removeAll(modsById.remove(provided));
		}
	}

	static void selectMod(ModCandidate mod, Map<String, ModCandidate> selectedMods, List<ModCandidate> uniqueSelectedMods) throws ModResolutionException {
		ModCandidate prev = selectedMods.put(mod.getId(), mod);
		if (prev != null) throw new ModResolutionException("duplicate mod %s", mod.getId());

		for (String provided : mod.getProvides()) {
			prev = selectedMods.put(provided, mod);
			if (prev != null) throw new ModResolutionException("duplicate mod %s", provided);
		}

		uniqueSelectedMods.add(mod);
	}
}
