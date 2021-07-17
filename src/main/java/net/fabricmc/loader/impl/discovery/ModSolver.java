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

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.sat4j.pb.IPBSolver;
import org.sat4j.pb.SolverFactory;
import org.sat4j.pb.tools.DependencyHelper;
import org.sat4j.pb.tools.INegator;
import org.sat4j.pb.tools.WeightedObject;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.discovery.Explanation.ErrorKind;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;

final class ModSolver {
	static Result solve(List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<String, ModCandidate> selectedMods, List<ModCandidate> uniqueSelectedMods) throws ContradictionException, TimeoutException, ModResolutionException {
		// build priority index

		Map<ModCandidate, Integer> priorities = new IdentityHashMap<>(allModsSorted.size());

		for (int i = 0; i < allModsSorted.size(); i++) {
			priorities.put(allModsSorted.get(i), i);
		}

		// create and configure solver

		solverPrepTime = System.nanoTime();

		IPBSolver solver = SolverFactory.newDefaultOptimizer();

		int timeout = Integer.getInteger(SystemProperties.DEBUG_RESOLUTION_TIMEOUT, 60);
		if (timeout > 0) solver.setTimeout(timeout); // in seconds

		DependencyHelper<DomainObject, Explanation> dependencyHelper = createDepHelper(solver);

		setupSolver(allModsSorted, modsById,
				priorities, selectedMods, uniqueSelectedMods,
				false, null, false,
				dependencyHelper);

		// solve

		solveTime = System.nanoTime();

		boolean hasSolution = dependencyHelper.hasASolution();

		// check solution

		solutionFetchTime = System.nanoTime();

		if (hasSolution) {
			Collection<DomainObject> solution = dependencyHelper.getASolution();

			solutionAnalyzeTime = System.nanoTime();

			for (DomainObject obj : solution) {
				if (obj instanceof ModCandidate) {
					ModResolver.selectMod((ModCandidate) obj, selectedMods, uniqueSelectedMods);
				} else {
					assert obj instanceof OptionalDepVar;
				}
			}

			dependencyHelper.reset();

			return Result.createSuccess();
		} else { // no solution
			Set<Explanation> reason = dependencyHelper.why();

			// gather all failed deps

			Set<ModDependency> failedDeps = Collections.newSetFromMap(new IdentityHashMap<>());
			List<Explanation> failedExplanations = new ArrayList<>();

			computeFailureCausesOptional(allModsSorted, modsById,
					priorities, selectedMods, uniqueSelectedMods,
					reason, dependencyHelper,
					failedDeps, failedExplanations);

			// find best solution with mod addition/removal

			fixSetupTime = System.nanoTime();

			Fix fix = computeFix(uniqueSelectedMods, allModsSorted, modsById,
					priorities, selectedMods,
					failedDeps, dependencyHelper);

			dependencyHelper.reset();

			return Result.createFailure(reason, failedExplanations, fix);
		}
	}

	static long solverPrepTime;
	static long solveTime;
	static long solutionFetchTime;
	static long solutionAnalyzeTime;
	static long fixSetupTime;

	static class Result {
		final boolean success;
		final Collection<Explanation> immediateReason;
		final Collection<Explanation> reason;
		final Fix fix; // may be null

		static Result createSuccess() {
			return new Result(true, null, null, null);
		}

		static Result createFailure(Collection<Explanation> immediateReason, Collection<Explanation> reason, Fix fix) {
			return new Result(false, immediateReason, reason, fix);
		}

		private Result(boolean success, Collection<Explanation> immediateReason, Collection<Explanation> reason, Fix fix) {
			this.success = success;
			this.immediateReason = immediateReason;
			this.reason = reason;
			this.fix = fix;
		}
	}

	private static void computeFailureCausesOptional(List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<ModCandidate, Integer> priorities, Map<String, ModCandidate> selectedMods, List<ModCandidate> uniqueSelectedMods,
			Set<Explanation> reason, DependencyHelper<DomainObject, Explanation> dependencyHelper,
			Set<ModDependency> failedDeps, List<Explanation> failedExplanations) throws ContradictionException, TimeoutException {
		dependencyHelper.reset();
		dependencyHelper = createDepHelper(dependencyHelper.getSolver()); // dependencyHelper.reset doesn't fully reset the dep helper

		setupSolver(allModsSorted, modsById,
				priorities, selectedMods, uniqueSelectedMods,
				true, null, false,
				dependencyHelper);

		if (dependencyHelper.hasASolution()) {
			Collection<DomainObject> solution = dependencyHelper.getASolution();
			Set<ModDependency> disabledDeps = Collections.newSetFromMap(new IdentityHashMap<>());

			for (DomainObject obj : solution) {
				if (obj instanceof DisableDepVar) {
					disabledDeps.add(((DisableDepVar) obj).dep);
				} else {
					assert obj instanceof ModCandidate;
				}
			}

			// populate failedDeps with disabledDeps entries that are actually in use (referenced through non-optional mods)
			// record explanation for failed deps that capture the depending mod

			for (DomainObject obj : solution) {
				if (!(obj instanceof ModCandidate)) continue;

				ModCandidate mod = (ModCandidate) obj;

				for (ModDependency dep : mod.getDependencies()) {
					if (disabledDeps.contains(dep)) {
						assert dep.getKind() == ModDependency.Kind.DEPENDS || dep.getKind() == ModDependency.Kind.BREAKS;

						failedDeps.add(dep);
						failedExplanations.add(new Explanation(dep.getKind() == ModDependency.Kind.DEPENDS ? ErrorKind.HARD_DEP : ErrorKind.NEG_HARD_DEP, mod, dep));
					}
				}
			}
		}
	}

	private static Fix computeFix(List<ModCandidate> uniqueSelectedMods, List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<ModCandidate, Integer> priorities, Map<String, ModCandidate> selectedMods,
			Set<ModDependency> failedDeps, DependencyHelper<DomainObject, Explanation> dependencyHelper) throws ContradictionException, TimeoutException {
		// group positive deps by mod id
		Map<String, Set<Collection<VersionPredicate>>> depsById = new HashMap<>();

		for (ModDependency dep : failedDeps) {
			if (dep.getKind() == ModDependency.Kind.DEPENDS) {
				depsById.computeIfAbsent(dep.getModId(), ignore -> new HashSet<>()).add(dep.getVersionRequirements());
			}
		}

		// determine mod versions to try to add

		Map<String, List<AddModVar>> installableMods = new HashMap<>();

		for (Map.Entry<String, Set<Collection<VersionPredicate>>> entry : depsById.entrySet()) {
			String id = entry.getKey();

			// extract all version bounds (resulting version needs to be part of one of them)

			Set<VersionInterval> allIntervals = new HashSet<>();

			for (Collection<VersionPredicate> versionPredicates : entry.getValue()) {
				List<VersionInterval> intervals = Collections.emptyList();

				for (VersionPredicate v : versionPredicates) {
					intervals = VersionInterval.or(intervals, v.getInterval());
				}

				allIntervals.addAll(intervals);
			}

			if (allIntervals.isEmpty()) continue;

			// try to determine common version bounds, alternatively approximate (imprecise due to not knowing the real versions or which deps are really essential)

			VersionInterval commonInterval = null;
			boolean commonVersionInitialized = false;
			Map<Version, VersionInterval> versions = new HashMap<>();

			for (VersionInterval interval : allIntervals) {
				if (commonInterval == null) {
					if (!commonVersionInitialized) { // initialize to first range, otherwise leave as empty range
						commonInterval = interval;
						commonVersionInitialized = true;
					}
				} else {
					commonInterval = interval.and(commonInterval);
				}

				Version version = deriveVersion(interval);

				VersionInterval prev = versions.putIfAbsent(version, interval);

				if (prev != null) {
					VersionInterval newBounds = VersionInterval.and(prev, interval);
					if (newBounds != null) versions.put(version, newBounds);
				}
			}

			List<AddModVar> out = installableMods.computeIfAbsent(id, ignore -> new ArrayList<>());

			if (commonInterval != null) {
				out.add(new AddModVar(id, deriveVersion(commonInterval), Collections.singletonList(commonInterval)));
			} else {
				for (Map.Entry<Version, VersionInterval> versionEntry : versions.entrySet()) {
					out.add(new AddModVar(id, versionEntry.getKey(), Collections.singletonList(versionEntry.getValue())));
				}
			}

			out.sort(Comparator.<AddModVar, Version>comparing(AddModVar::getVersion).reversed());
		}

		// check the determined solution

		fixSolveTime = System.nanoTime();

		dependencyHelper.reset();
		dependencyHelper = createDepHelper(dependencyHelper.getSolver()); // dependencyHelper.reset doesn't fully reset the dep helper

		setupSolver(allModsSorted, modsById,
				priorities, selectedMods, uniqueSelectedMods,
				false, installableMods, true,
				dependencyHelper);

		if (!dependencyHelper.hasASolution()) {
			Log.warn(LogCategory.RESOLUTION, "Unable to find a solution to fix the mod set, reason: %s", dependencyHelper.why());
			return null;
		}

		List<ModCandidate> activeMods = new ArrayList<>();
		List<AddModVar> modsToAdd = new ArrayList<>();
		List<ModCandidate> modsToRemove = new ArrayList<>();
		Map<AddModVar, List<ModCandidate>> modReplacements = new HashMap<>();

		for (DomainObject obj : dependencyHelper.getASolution()) {
			if (obj instanceof ModCandidate) {
				activeMods.add((ModCandidate) obj);
			} else if (obj instanceof AddModVar) {
				List<ModCandidate> replaced = new ArrayList<>();

				ModCandidate selectedMod = selectedMods.get(obj.getId());
				if (selectedMod != null) replaced.add(selectedMod);

				List<ModCandidate> mods = modsById.get(obj.getId());

				if (mods != null) replaced.addAll(mods);

				if (replaced.isEmpty()) {
					modsToAdd.add((AddModVar) obj);
				} else {
					modReplacements.put((AddModVar) obj, replaced);
				}
			} else if (obj instanceof RemoveModVar) {
				boolean found = false;

				ModCandidate mod = selectedMods.get(obj.getId());

				if (mod != null) {
					modsToRemove.add(mod);
					found = true;
				}

				List<ModCandidate> mods = modsById.get(obj.getId());

				if (mods != null) {
					for (ModCandidate m : mods) {
						if (m.isRoot()) {
							modsToRemove.add(m);
							found = true;
						}
					}
				}

				assert found;
			} else { // unexpected domainobj kind
				assert false : obj;
			}
		}

		// TODO: test if the solution is actually valid?

		return new Fix(modsToAdd, modsToRemove, modReplacements, activeMods);
	}

	static long fixSolveTime;

	private static Version deriveVersion(VersionInterval interval) {
		if (!interval.isSemantic()) {
			return interval.getMin() != null ? interval.getMin() : interval.getMax();
		}

		SemanticVersion v = (SemanticVersion) interval.getMin();

		if (v != null) { // min bound present
			if (!interval.isMinInclusive()) { // not inclusive, increment slightly
				String pr = v.getPrereleaseKey().orElse(null);
				int[] comps = ((SemanticVersionImpl) v).getVersionComponents();

				if (pr != null) { // has prerelease, add to increase
					pr = pr.isEmpty() ? "0" : pr.concat(".0");
				} else { // regular version only, increment patch and make least prerelease
					if (comps.length < 3) {
						comps = Arrays.copyOf(comps, comps.length + 1);
					}

					comps[comps.length - 1]++;
					pr = "";
				}

				v = new SemanticVersionImpl(comps, pr, null);
			}
		} else if ((v = (SemanticVersion) interval.getMax()) != null) { // max bound only
			if (!interval.isMaxInclusive()) { // not inclusive, decrement slightly
				String pr = v.getPrereleaseKey().orElse(null);
				int[] comps = ((SemanticVersionImpl) v).getVersionComponents();

				if (pr == null) { // no prerelease, use large pr segment
					pr = "zzzzzzzz";
				} else if (!pr.isEmpty()) { // non-empty prerelease present, decrement slightly or truncate
					int pos = pr.lastIndexOf('.') + 1;
					String suffix = pr.substring(pos);
					int val;
					char c;

					if (suffix.matches("\\d+") && (val = Integer.parseInt(suffix)) > 0) {
						pr = pr.substring(0, pos)+(val - 1);
					} else if (suffix.length() > 0 && ((c = suffix.charAt(suffix.length() - 1)) != '0' || suffix.length() >= 2)) {
						pr = pr.substring(0, pr.length() - 1);

						if (c == 'a') {
							pr += 'Z';
						} else if (c == 'A') {
							pr += '9';
						} else if (c != '0') {
							pr += c - 1;
						}
					} else {
						pr = pos > 0 ? pr.substring(0, pos - 1) : "";
					}
				} else { // empty prerelease, decrement version and strip prerelease
					pr = null;

					if (comps.length < 3) {
						comps = Arrays.copyOf(comps, 3);
					}

					for (int i = 2; i >= 0; i--) {
						if (comps[i] > 0) {
							comps[i]--;
							break;
						} else {
							comps[i] = 9999;
						}
					}
				}

				v = new SemanticVersionImpl(comps, pr, null);
			}
		} else { // unbounded
			v = new SemanticVersionImpl(new int[] { 1 }, null, null);
		}

		return v;
	}

	static class Fix {
		final Collection<AddModVar> modsToAdd;
		final Collection<ModCandidate> modsToRemove;
		final Map<AddModVar, List<ModCandidate>> modReplacements;
		final Collection<ModCandidate> activeMods;

		Fix(Collection<AddModVar> modsToAdd, Collection<ModCandidate> modsToRemove, Map<AddModVar, List<ModCandidate>> modReplacements,
				Collection<ModCandidate> activeMods) {
			this.modsToAdd = modsToAdd;
			this.modsToRemove = modsToRemove;
			this.modReplacements = modReplacements;
			this.activeMods = activeMods;
		}
	}

	private static void setupSolver(List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<ModCandidate, Integer> priorities, Map<String, ModCandidate> selectedMods, List<ModCandidate> uniqueSelectedMods,
			boolean depDisableSim, Map<String, List<AddModVar>> installableMods, boolean removalSim,
			DependencyHelper<DomainObject, Explanation> dependencyHelper) throws ContradictionException {
		Map<String, DomainObject> dummies = new HashMap<>();
		Map<ModDependency, Map.Entry<DomainObject, Integer>> disabledDeps = depDisableSim ? new HashMap<>() : null;
		List<WeightedObject<DomainObject>> weightedObjects = new ArrayList<>();

		generatePreselectConstraints(uniqueSelectedMods, modsById,
				priorities, selectedMods,
				depDisableSim, installableMods, removalSim,
				dummies, disabledDeps,
				dependencyHelper, weightedObjects);

		generateMainConstraints(allModsSorted, modsById,
				priorities, selectedMods,
				depDisableSim, installableMods, removalSim,
				dummies, disabledDeps,
				dependencyHelper, weightedObjects);

		if (depDisableSim) {
			applyDisableDepVarWeights(disabledDeps, priorities.size(), weightedObjects);
		}

		@SuppressWarnings("unchecked")
		WeightedObject<DomainObject>[] weights = weightedObjects.toArray(new WeightedObject[0]);
		dependencyHelper.setObjectiveFunction(weights);
		//dependencyHelper.addWeightedCriterion(weightedObjects);
	}

	private static void generatePreselectConstraints(List<ModCandidate> uniqueSelectedMods, Map<String, List<ModCandidate>> modsById,
			Map<ModCandidate, Integer> priorities, Map<String, ModCandidate> selectedMods,
			boolean depDisableSim, Map<String, List<AddModVar>> installableMods, boolean removalSim,
			Map<String, DomainObject> dummyMods, Map<ModDependency, Map.Entry<DomainObject, Integer>> disabledDeps,
			DependencyHelper<DomainObject, Explanation> dependencyHelper, List<WeightedObject<DomainObject>> weightedObjects) throws ContradictionException {
		boolean enableOptional = !depDisableSim && installableMods == null && !removalSim; // whether to enable optional mods (regular solve only, not for failure handling)
		List<DomainObject> suitableMods = new ArrayList<>();

		for (ModCandidate mod : uniqueSelectedMods) {
			// add constraints for dependencies (skips deps that are already preselected outside depDisableSim)

			for (ModDependency dep : mod.getDependencies()) {
				if (!enableOptional && dep.getKind().isSoft()) continue;
				if (selectedMods.containsKey(dep.getModId())) continue;

				List<? extends DomainObject.Mod> availableMods = modsById.get(dep.getModId());

				if (availableMods != null) {
					for (DomainObject.Mod m : availableMods) {
						if (dep.matches(m.getVersion())) suitableMods.add(m);
					}
				}

				if (installableMods != null) {
					availableMods = installableMods.get(dep.getModId());

					if (availableMods != null) {
						for (DomainObject.Mod m : availableMods) {
							if (dep.matches(m.getVersion())) suitableMods.add(m);
						}
					}
				}

				if (suitableMods.isEmpty() && !depDisableSim) continue;

				switch (dep.getKind()) {
				case DEPENDS:
					if (depDisableSim) {
						suitableMods.add(getCreateDisableDepVar(dep, disabledDeps));
					}

					dependencyHelper.clause(new Explanation(ErrorKind.PRESELECT_HARD_DEP, mod, dep), suitableMods.toArray(new DomainObject[0]));
					break;
				case RECOMMENDS:
					// this will prioritize greedy over non-greedy loaded mods, regardless of modPrioComparator due to the objective weights

					// only pull IF_RECOMMENDED or encompassing in
					suitableMods.removeIf(m -> ((ModCandidate) m).getLoadCondition().ordinal() > ModLoadCondition.IF_RECOMMENDED.ordinal());

					if (!suitableMods.isEmpty()) {
						suitableMods.add(getCreateDummy(dep.getModId(), OptionalDepVar::new, dummyMods, priorities.size(), weightedObjects));
						dependencyHelper.clause(new Explanation(ErrorKind.PRESELECT_SOFT_DEP, mod, dep), suitableMods.toArray(new DomainObject[0]));
					}

					break;
				case BREAKS:
					if (depDisableSim) {
						dependencyHelper.setTrue(getCreateDisableDepVar(dep, disabledDeps), new Explanation(ErrorKind.PRESELECT_NEG_HARD_DEP, mod, dep));
					} else {
						for (DomainObject match : suitableMods) {
							dependencyHelper.setFalse(match, new Explanation(ErrorKind.PRESELECT_NEG_HARD_DEP, mod, dep));
						}
					}

					break;
				case CONFLICTS:
					// TODO: soft negative dep?
					break;
				default:
					// ignore
				}

				suitableMods.clear();
			}

			if (removalSim) {
				int prio = priorities.size() + 10;

				if (installableMods != null) {
					prio += installableMods.getOrDefault(mod.getId(), Collections.emptyList()).size();

					List<AddModVar> installable = installableMods.get(mod.getId());
					if (installable != null) suitableMods.addAll(installable);
				}

				suitableMods.add(getCreateDummy(mod.getId(), RemoveModVar::new, dummyMods, prio, weightedObjects));
				suitableMods.add(mod);

				dependencyHelper.clause(new Explanation(ErrorKind.PRESELECT_FORCELOAD, mod.getId()), suitableMods.toArray(new DomainObject[0]));
				suitableMods.clear();
			}
		}
	}

	private static void generateMainConstraints(List<ModCandidate> allModsSorted, Map<String, List<ModCandidate>> modsById,
			Map<ModCandidate, Integer> priorities, Map<String, ModCandidate> selectedMods,
			boolean depDisableSim, Map<String, List<AddModVar>> installableMods, boolean removalSim,
			Map<String, DomainObject> dummyMods, Map<ModDependency, Map.Entry<DomainObject, Integer>> disabledDeps,
			DependencyHelper<DomainObject, Explanation> dependencyHelper, List<WeightedObject<DomainObject>> weightedObjects) throws ContradictionException {
		boolean enableOptional = !depDisableSim && installableMods == null && !removalSim; // whether to enable optional mods (regular solve only, not for failure handling)
		List<DomainObject> suitableMods = new ArrayList<>();

		for (ModCandidate mod : allModsSorted) {
			// add constraints for dependencies

			for (ModDependency dep : mod.getDependencies()) {
				if (!enableOptional && dep.getKind().isSoft()) continue;

				ModCandidate selectedMod = selectedMods.get(dep.getModId());

				if (selectedMod != null) { // dep is already selected = present
					if (!removalSim) {
						if (dep.getKind() == ModDependency.Kind.DEPENDS && !dep.matches(selectedMod.getVersion())) { // ..but isn't suitable
							if (depDisableSim) {
								dependencyHelper.setTrue(getCreateDisableDepVar(dep, disabledDeps), new Explanation(ErrorKind.HARD_DEP, mod, dep));
							} else {
								dependencyHelper.setFalse(mod, new Explanation(ErrorKind.HARD_DEP_INCOMPATIBLE_PRESELECTED, mod, dep));
							}
						}

						continue;
					} else if (dep.matches(selectedMod.getVersion())) {
						suitableMods.add(selectedMod);
					}
				}

				List<? extends DomainObject.Mod> availableMods = modsById.get(dep.getModId());

				if (availableMods != null) {
					for (DomainObject.Mod m : availableMods) {
						if (dep.matches(m.getVersion())) suitableMods.add(m);
					}
				}

				if (installableMods != null) {
					availableMods = installableMods.get(dep.getModId());

					if (availableMods != null) {
						for (DomainObject.Mod m : availableMods) {
							if (dep.matches(m.getVersion())) suitableMods.add(m);
						}
					}
				}

				switch (dep.getKind()) {
				case DEPENDS: // strong dep
					if (depDisableSim) {
						suitableMods.add(getCreateDisableDepVar(dep, disabledDeps));
					}

					if (suitableMods.isEmpty()) {
						dependencyHelper.setFalse(mod, new Explanation(ErrorKind.HARD_DEP_NO_CANDIDATE, mod, dep));
					} else {
						dependencyHelper.implication(mod).implies(suitableMods.toArray(new DomainObject[0])).named(new Explanation(ErrorKind.HARD_DEP, mod, dep));
					}

					break;
				case RECOMMENDS: // soft dep
					// this will prioritize greedy over non-greedy loaded mods, regardless of modPrioComparator due to the objective weights

					// only pull IF_RECOMMENDED or encompassing in
					suitableMods.removeIf(m -> ((ModCandidate) m).getLoadCondition().ordinal() > ModLoadCondition.IF_RECOMMENDED.ordinal());

					if (!suitableMods.isEmpty()) {
						suitableMods.add(getCreateDummy(dep.getModId(), OptionalDepVar::new, dummyMods, priorities.size(), weightedObjects));
						dependencyHelper.implication(mod).implies(suitableMods.toArray(new DomainObject[0])).named(new Explanation(ErrorKind.SOFT_DEP, mod, dep));
					}

					break;
				case BREAKS: // strong negative dep
					if (!suitableMods.isEmpty()) {
						if (depDisableSim) {
							DomainObject var = getCreateDisableDepVar(dep, disabledDeps);

							for (DomainObject match : suitableMods) {
								dependencyHelper.implication(mod).implies(new NegatedDomainObject(match), var).named(new Explanation(ErrorKind.NEG_HARD_DEP, mod, dep));
							}
						} else {
							for (DomainObject match : suitableMods) {
								dependencyHelper.implication(mod).impliesNot(match).named(new Explanation(ErrorKind.NEG_HARD_DEP, mod, dep));
							}
						}
					}

					break;
				case CONFLICTS:
					// TODO: soft negative dep?
					break;
				default:
					// ignore
				}

				suitableMods.clear();
			}

			// add constraints to select greedy nested mods (ALWAYS or IF_POSSIBLE)
			// add constraints to restrict nested mods to selected parents

			if (!mod.isRoot()) { // nested mod
				ModLoadCondition loadCondition = mod.getLoadCondition();

				if (loadCondition == ModLoadCondition.ALWAYS) { // required with parent
					Explanation explanation = new Explanation(ErrorKind.NESTED_FORCELOAD, mod.getParentMods().iterator().next(), mod.getId()); // FIXME: this applies to all parents
					DomainObject[] siblings = modsById.get(mod.getId()).toArray(new DomainObject[0]);

					if (isAnyParentSelected(mod, selectedMods)) {
						dependencyHelper.clause(explanation, siblings);
					} else {
						for (ModCandidate parent : mod.getParentMods()) {
							dependencyHelper.implication(parent).implies(siblings).named(explanation);
						}
					}
				}

				// require parent to be selected with the nested mod

				if (!isAnyParentSelected(mod, selectedMods)) {
					dependencyHelper.implication(mod).implies(mod.getParentMods().toArray(new DomainObject[0])).named(new Explanation(ErrorKind.NESTED_REQ_PARENT, mod));
				}
			}

			// add weights if potentially needed (choice between multiple mods or dummies)

			if (!mod.isRoot() || mod.getLoadCondition() != ModLoadCondition.ALWAYS || modsById.get(mod.getId()).size() > 1) {
				int prio = priorities.get(mod);
				BigInteger weight;

				if (mod.getLoadCondition().ordinal() >= ModLoadCondition.IF_RECOMMENDED.ordinal()) { // non-greedy (optional)
					weight = TWO.pow(prio + 1);
				} else { // greedy
					weight = TWO.pow(allModsSorted.size() - prio).negate();
				}

				weightedObjects.add(WeightedObject.newWO(mod, weight));
			}
		}

		// add constraints to force-load root mods (ALWAYS only, IF_POSSIBLE is being handled through negative weight later)
		// add single mod per id constraints

		for (List<ModCandidate> variants : modsById.values()) {
			ModCandidate firstMod = variants.get(0);
			String id = firstMod.getId();

			// force-load root mod

			if (variants.size() == 1 && !removalSim) { // trivial case, others are handled by multi-variant impl
				if (firstMod.isRoot() && firstMod.getLoadCondition() == ModLoadCondition.ALWAYS) {
					dependencyHelper.setTrue(firstMod, new Explanation(ErrorKind.ROOT_FORCELOAD_SINGLE, firstMod));
				}
			} else { // complex case, potentially multiple variants
				boolean isRequired = false;

				for (ModCandidate mod : variants) {
					if (mod.isRoot() && mod.getLoadCondition() == ModLoadCondition.ALWAYS) {
						isRequired = true;
						break;
					}
				}

				if (isRequired) {
					if (removalSim) {
						int prio = priorities.size() + 10;
						if (installableMods != null) prio += installableMods.getOrDefault(id, Collections.emptyList()).size();

						suitableMods.add(getCreateDummy(id, RemoveModVar::new, dummyMods, prio, weightedObjects));
					}

					if (installableMods != null) {
						List<AddModVar> installable = installableMods.get(id);
						if (installable != null) suitableMods.addAll(installable);
					}

					suitableMods.addAll(variants);

					dependencyHelper.clause(new Explanation(ErrorKind.ROOT_FORCELOAD, id), suitableMods.toArray(new DomainObject[0]));
					suitableMods.clear();
				}
			}

			// single mod per id constraint

			suitableMods.addAll(variants);

			if (installableMods != null) {
				List<AddModVar> installable = installableMods.get(id);

				if (installable != null && !installable.isEmpty()) {
					suitableMods.addAll(installable);

					ModCandidate mod = selectedMods.get(id);
					if (mod != null) suitableMods.add(mod);
				}
			}

			if (suitableMods.size() > 1 // multiple options
					|| enableOptional && firstMod.getLoadCondition() == ModLoadCondition.IF_POSSIBLE) { // optional greedy loading
				dependencyHelper.atMost(1, suitableMods.toArray(new DomainObject[0])).named(new Explanation(ErrorKind.UNIQUE_ID, id));
			}

			suitableMods.clear();
		}

		// add weights and missing unique id constraints for installable mods

		if (installableMods != null) {
			for (List<AddModVar> variants : installableMods.values()) {
				String id = variants.get(0).getId();

				if (!modsById.containsKey(id)) { // no single mod per id constraint yet
					suitableMods.addAll(variants);

					ModCandidate selectedMod = selectedMods.get(id);
					if (selectedMod != null) suitableMods.add(selectedMod);

					if (suitableMods.size() > 1) {
						dependencyHelper.atMost(1, suitableMods.toArray(new DomainObject[0])).named(new Explanation(ErrorKind.UNIQUE_ID, id));
					}

					suitableMods.clear();
				}

				for (int i = 0; i < variants.size(); i++) {
					AddModVar mod = variants.get(i);
					weightedObjects.add(WeightedObject.newWO(mod, TWO.pow(priorities.size() + 4 + i)));
				}
			}
		}
	}

	private static final BigInteger TWO = BigInteger.valueOf(2);

	private static DependencyHelper<DomainObject, Explanation> createDepHelper(IPBSolver solver) {
		DependencyHelper<DomainObject, Explanation> ret = new DependencyHelper<>(solver); // new LexicoHelper<>(solver)
		ret.setNegator(negator);

		return ret;
	}

	private static DomainObject getCreateDummy(String id, Function<String, DomainObject> supplier, Map<String, DomainObject> duplicateMap, int modCount, List<WeightedObject<DomainObject>> weightedObjects) {
		DomainObject ret = duplicateMap.get(id);
		if (ret != null) return ret;

		ret = supplier.apply(id);
		weightedObjects.add(WeightedObject.newWO(ret, TWO.pow(modCount + 2)));

		return ret;
	}

	private static DomainObject getCreateDisableDepVar(ModDependency dep, Map<ModDependency, Map.Entry<DomainObject, Integer>> duplicateMap) {
		Map.Entry<DomainObject, Integer> entry = duplicateMap.computeIfAbsent(dep, d -> new AbstractMap.SimpleEntry<>(new DisableDepVar(d), 0));
		entry.setValue(entry.getValue() + 1);

		return entry.getKey();
	}

	private static void applyDisableDepVarWeights(Map<ModDependency, Map.Entry<DomainObject, Integer>> map, int modCount, List<WeightedObject<DomainObject>> weightedObjects) {
		BigInteger baseWeight = TWO.pow(modCount + 3);

		for (Map.Entry<DomainObject, Integer> entry : map.values()) {
			int count = entry.getValue();
			weightedObjects.add(WeightedObject.newWO(entry.getKey(), count > 1 ? baseWeight.multiply(BigInteger.valueOf(count)) : baseWeight));
		}
	}

	private static final class OptionalDepVar implements DomainObject {
		private final String id;

		OptionalDepVar(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String toString() {
			return "optionalDep:"+getId();
		}
	}

	private static final class DisableDepVar implements DomainObject {
		final ModDependency dep;

		DisableDepVar(ModDependency dep) {
			this.dep = dep;
		}

		@Override
		public String getId() {
			return dep.getModId();
		}

		@Override
		public String toString() {
			return "disableDep:"+dep;
		}
	}

	static final class AddModVar implements DomainObject.Mod {
		private final String id;
		private final Version version;
		private final List<VersionInterval> versionIntervals;

		AddModVar(String id, Version version, List<VersionInterval> versionIntervals) {
			this.id = id;
			this.version = version;
			this.versionIntervals = versionIntervals;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public Version getVersion() {
			return version;
		}

		public List<VersionInterval> getVersionIntervals() {
			return versionIntervals;
		}

		@Override
		public String toString() {
			return String.format("add:%s %s (%s)", id, version, versionIntervals);
		}
	}

	private static final class RemoveModVar implements DomainObject {
		private final String id;

		RemoveModVar(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String toString() {
			return "remove:"+getId();
		}
	}

	private static final class NegatedDomainObject implements DomainObject {
		private final DomainObject obj;

		NegatedDomainObject(DomainObject obj) {
			this.obj = obj;
		}

		@Override
		public String getId() {
			return obj.getId();
		}

		@Override
		public String toString() {
			return "!"+obj;
		}
	}

	private static final INegator negator = new INegator() {
		@Override
		public Object unNegate(Object thing) {
			return ((NegatedDomainObject) thing).obj;
		}

		@Override
		public boolean isNegated(Object thing) {
			return thing instanceof NegatedDomainObject;
		}
	};

	static boolean isAnyParentSelected(ModCandidate mod, Map<String, ModCandidate> selectedMods) {
		for (ModCandidate parentMod : mod.getParentMods()) {
			if (selectedMods.get(parentMod.getId()) == parentMod) return true;
		}

		return false;
	}

	static boolean hasAllDepsSatisfied(ModCandidate mod, Map<String, ModCandidate> mods) {
		for (ModDependency dep : mod.getDependencies()) {
			if (dep.getKind() == ModDependency.Kind.DEPENDS) {
				ModCandidate m = mods.get(dep.getModId());
				if (m == null || !dep.matches(m.getVersion())) return false;
			} else if (dep.getKind() == ModDependency.Kind.BREAKS) {
				ModCandidate m = mods.get(dep.getModId());
				if (m != null && dep.matches(m.getVersion())) return false;
			}
		}

		return true;
	}
}
