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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.discovery.ModSolver.AddModVar;
import net.fabricmc.loader.impl.discovery.ModSolver.InactiveReason;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.util.Localization;
import net.fabricmc.loader.impl.util.StringUtil;
import net.fabricmc.loader.impl.util.version.VersionIntervalImpl;

final class ResultAnalyzer {
	private static final boolean SHOW_PATH_INFO = false;
	private static final boolean SHOW_INACTIVE = false;

	@SuppressWarnings("unused")
	static String gatherErrors(ModSolver.Result result, Map<String, ModCandidateImpl> selectedMods, Map<String, List<ModCandidateImpl>> modsById,
			Map<String, Set<ModCandidateImpl>> envDisabledMods, EnvType envType) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			String prefix = "";
			boolean suggestFix = true;

			if (result.fix != null) {
				pw.printf("\n%s", Localization.format("resolution.solutionHeader"));

				formatFix(result.fix, result, selectedMods, modsById, envDisabledMods, envType, pw);

				pw.printf("\n%s", Localization.format("resolution.depListHeader"));
				prefix = "\t";
				suggestFix = false;
			}

			List<ModCandidateImpl> matches = new ArrayList<>();

			for (Explanation explanation : result.reason) {
				assert explanation.error.isDependencyError;

				ModDependency dep = explanation.dep;
				ModCandidateImpl selected = selectedMods.get(dep.getModId());

				if (selected != null) {
					matches.add(selected);
				} else {
					List<ModCandidateImpl> candidates = modsById.get(dep.getModId());
					if (candidates != null) matches.addAll(candidates);
				}

				addErrorToList(explanation.mod, explanation.dep, matches, envDisabledMods.containsKey(dep.getModId()), suggestFix, prefix, pw);
				matches.clear();
			}

			if (SHOW_INACTIVE && result.fix != null && !result.fix.inactiveMods.isEmpty()) {
				pw.printf("\n%s", Localization.format("resolution.inactiveMods"));

				List<Map.Entry<ModCandidateImpl, InactiveReason>> entries = new ArrayList<>(result.fix.inactiveMods.entrySet());

				// sort by root, id, version
				entries.sort(new Comparator<Map.Entry<ModCandidateImpl, ?>>() {
					@Override
					public int compare(Entry<ModCandidateImpl, ?> o1, Entry<ModCandidateImpl, ?> o2) {
						ModCandidateImpl a = o1.getKey();
						ModCandidateImpl b = o2.getKey();

						if (a.isRoot() != b.isRoot()) {
							return a.isRoot() ? -1 : 1;
						}

						return ModCandidateImpl.ID_VERSION_COMPARATOR.compare(a, b);
					}
				});

				for (Map.Entry<ModCandidateImpl, InactiveReason> entry : entries) {
					ModCandidateImpl mod = entry.getKey();
					InactiveReason reason = entry.getValue();
					String reasonKey = String.format("resolution.inactive.%s", reason.id);

					pw.printf("\n\t - %s", Localization.format("resolution.inactive",
							getName(mod),
							getVersion(mod),
							Localization.format(reasonKey)));
					//appendJijInfo(mod, "\t", false, pw); TODO: show this without spamming too much
				}
			}
		}

		return sw.toString();
	}

	private static void formatFix(ModSolver.Fix fix,
			ModSolver.Result result, Map<String, ModCandidateImpl> selectedMods, Map<String, List<ModCandidateImpl>> modsById,
			Map<String, Set<ModCandidateImpl>> envDisabledMods, EnvType envType,
			PrintWriter pw) {
		for (AddModVar mod : fix.modsToAdd) {
			Set<ModCandidateImpl> envDisabledAlternatives = envDisabledMods.get(mod.getId());

			if (envDisabledAlternatives == null) {
				pw.printf("\n\t - %s", Localization.format("resolution.solution.addMod",
						mod.getId(),
						formatVersionRequirements(mod.getVersionIntervals())));
			} else {
				String envKey = String.format("environment.%s", envType.name().toLowerCase(Locale.ENGLISH));

				pw.printf("\n\t - %s", Localization.format("resolution.solution.replaceModEnvDisabled",
						formatOldMods(envDisabledAlternatives),
						mod.getId(),
						formatVersionRequirements(mod.getVersionIntervals()),
						Localization.format(envKey)));
			}
		}

		for (ModCandidateImpl mod : fix.modsToRemove) {
			pw.printf("\n\t - %s", Localization.format("resolution.solution.removeMod", getName(mod), getVersion(mod), mod.getLocalPath()));
		}

		for (Entry<AddModVar, List<ModCandidateImpl>> entry : fix.modReplacements.entrySet()) {
			AddModVar newMod = entry.getKey();
			List<ModCandidateImpl> oldMods = entry.getValue();
			String oldModsFormatted = formatOldMods(oldMods);

			if (oldMods.size() != 1 || !oldMods.get(0).getId().equals(newMod.getId())) { // replace mods with another mod (different mod id)
				String newModName = newMod.getId();
				ModCandidateImpl alt = selectedMods.get(newMod.getId());

				if (alt != null) {
					newModName = getName(alt);
				} else {
					List<ModCandidateImpl> alts = modsById.get(newMod.getId());
					if (alts != null && !alts.isEmpty()) newModName = getName(alts.get(0));
				}

				pw.printf("\n\t - %s", Localization.format("resolution.solution.replaceMod",
						oldModsFormatted,
						newModName,
						formatVersionRequirements(newMod.getVersionIntervals())));
			} else { // replace mod version only
				ModCandidateImpl oldMod = oldMods.get(0);
				boolean hasOverlap = !VersionInterval.and(newMod.getVersionIntervals(),
						Collections.singletonList(new VersionIntervalImpl(oldMod.getVersion(), true, oldMod.getVersion(), true))).isEmpty();

				if (!hasOverlap) { // required version range doesn't overlap installed version, recommend range as-is
					pw.printf("\n\t - %s", Localization.format("resolution.solution.replaceModVersion",
							oldModsFormatted,
							formatVersionRequirements(newMod.getVersionIntervals())));
				} else { // required version range overlaps installed version, recommend range without
					pw.printf("\n\t - %s", Localization.format("resolution.solution.replaceModVersionDifferent",
							oldModsFormatted,
							formatVersionRequirements(newMod.getVersionIntervals())));

					boolean foundAny = false;

					// check old deps against future mod set to highlight inconsistencies
					for (ModDependency dep : oldMod.getDependencies()) {
						if (dep.getKind().isSoft()) continue;

						ModCandidateImpl mod = fix.activeMods.get(dep.getModId());

						if (mod != null) {
							if (dep.matches(mod.getVersion()) != dep.getKind().isPositive()) {
								pw.printf("\n\t\t - %s", Localization.format("resolution.solution.replaceModVersionDifferent.reqSupportedModVersion",
										mod.getId(),
										getVersion(mod)));
								foundAny = true;
							}

							continue;
						}

						for (AddModVar addMod : fix.modReplacements.keySet()) {
							if (addMod.getId().equals(dep.getModId())) {
								pw.printf("\n\t\t - %s", Localization.format("resolution.solution.replaceModVersionDifferent.reqSupportedModVersions",
										addMod.getId(),
										formatVersionRequirements(addMod.getVersionIntervals())));
								foundAny = true;
								break;
							}
						}
					}

					if (!foundAny) {
						pw.printf("\n\t\t - %s", Localization.format("resolution.solution.replaceModVersionDifferent.unknown"));
					}
				}
			}
		}
	}

	static String gatherWarnings(List<ModCandidateImpl> uniqueSelectedMods, Map<String, ModCandidateImpl> selectedMods,
			Map<String, Set<ModCandidateImpl>> envDisabledMods, EnvType envType) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			for (ModCandidateImpl mod : uniqueSelectedMods) {
				for (ModDependency dep : mod.getDependencies()) {
					ModCandidateImpl depMod;

					switch (dep.getKind()) {
					case RECOMMENDS:
						depMod = selectedMods.get(dep.getModId());

						if (depMod == null || !dep.matches(depMod.getVersion())) {
							addErrorToList(mod, dep, toList(depMod), envDisabledMods.containsKey(dep.getModId()), true, "", pw);
						}

						break;
					case CONFLICTS:
						depMod = selectedMods.get(dep.getModId());

						if (depMod != null && dep.matches(depMod.getVersion())) {
							addErrorToList(mod, dep, toList(depMod), false, true, "", pw);
						}

						break;
					default:
						// ignore
					}
				}
			}
		}

		if (sw.getBuffer().length() == 0) {
			return null;
		} else {
			return sw.toString();
		}
	}

	private static List<ModCandidateImpl> toList(ModCandidateImpl mod) {
		return mod != null ? Collections.singletonList(mod) : Collections.emptyList();
	}

	private static void addErrorToList(ModCandidateImpl mod, ModDependency dep, List<ModCandidateImpl> matches, boolean presentForOtherEnv, boolean suggestFix, String prefix, PrintWriter pw) {
		Object[] args = new Object[] {
				getName(mod),
				getVersion(mod),
				(matches.isEmpty() ? dep.getModId() : getName(matches.get(0))),
				formatVersionRequirements(dep.getVersionIntervals()),
				getVersions(matches),
				matches.size()
		};

		String reason;

		if (!matches.isEmpty()) {
			boolean present;

			if (dep.getKind().isPositive()) {
				present = false;

				for (ModCandidateImpl match : matches) {
					if (dep.matches(match.getVersion())) { // there is a satisfying mod version, but it can't be loaded for other reasons
						present = true;
						break;
					}
				}
			} else {
				present = true;
			}

			reason = present ? "invalid" : "mismatch";
		} else if (presentForOtherEnv && dep.getKind().isPositive()) {
			reason = "envDisabled";
		} else {
			reason = "missing";
		}

		String key = String.format("resolution.%s.%s", dep.getKind().getKey(), reason);
		pw.printf("\n%s - %s", prefix, StringUtil.capitalize(Localization.format(key, args)));

		if (suggestFix) {
			key = String.format("resolution.%s.suggestion", dep.getKind().getKey());
			pw.printf("\n%s\t - %s", prefix, StringUtil.capitalize(Localization.format(key, args)));
		}

		if (SHOW_PATH_INFO) {
			for (ModCandidateImpl m : matches) {
				appendJijInfo(m, prefix, true, pw);
			}
		}
	}

	private static void appendJijInfo(ModCandidateImpl mod, String prefix, boolean mentionMod, PrintWriter pw) {
		String loc;
		String path;

		if (mod.getMetadata().getType().equals(AbstractModMetadata.TYPE_BUILTIN)) {
			loc = "builtin";
			path = null;
		} else if (mod.isRoot()) {
			loc = "root";
			path = mod.getLocalPath();
		} else {
			loc = "normal";

			List<ModCandidateImpl> paths = new ArrayList<>();
			paths.add(mod);

			ModCandidateImpl cur = mod;

			do {
				ModCandidateImpl best = null;
				int maxDiff = 0;

				for (ModCandidateImpl parent : cur.getParentMods()) {
					int diff = cur.getMinNestLevel() - parent.getMinNestLevel();

					if (diff > maxDiff) {
						best = parent;
						maxDiff = diff;
					}
				}

				if (best == null) break;

				paths.add(best);
				cur = best;
			} while (!cur.isRoot());

			StringBuilder pathSb = new StringBuilder();

			for (int i = paths.size() - 1; i >= 0; i--) {
				ModCandidateImpl m = paths.get(i);

				if (pathSb.length() > 0) pathSb.append(" -> ");
				pathSb.append(m.getLocalPath());
			}

			path = pathSb.toString();
		}

		String key = String.format("resolution.jij.%s%s", loc, mentionMod ? "" : "NoMention");
		String text;

		if (mentionMod) {
			if (path == null) {
				text = Localization.format(key, getName(mod), getVersion(mod));
			} else {
				text = Localization.format(key, getName(mod), getVersion(mod), path);
			}
		} else {
			if (path == null) {
				text = Localization.format(key);
			} else {
				text = Localization.format(key, path);
			}
		}

		pw.printf("\n%s\t - %s",
				prefix,
				StringUtil.capitalize(text));
	}

	@SuppressWarnings("unused")
	private static String formatOldMods(Collection<ModCandidateImpl> mods) {
		List<ModCandidateImpl> modsSorted = new ArrayList<>(mods);
		modsSorted.sort(ModCandidateImpl.ID_VERSION_COMPARATOR);
		List<String> ret = new ArrayList<>(modsSorted.size());

		for (ModCandidateImpl m : modsSorted) {
			if (SHOW_PATH_INFO && m.hasPath() && !m.isBuiltin()) {
				ret.add(Localization.format("resolution.solution.replaceMod.oldMod", getName(m), getVersion(m), m.getLocalPath()));
			} else {
				ret.add(Localization.format("resolution.solution.replaceMod.oldModNoPath", getName(m), getVersion(m)));
			}
		}

		return formatEnumeration(ret, true);
	}

	private static String getName(ModCandidateImpl candidate) {
		String typePrefix;

		switch (candidate.getMetadata().getType()) {
		case AbstractModMetadata.TYPE_FABRIC_MOD:
			typePrefix = String.format("%s ", Localization.format("resolution.type.mod"));
			break;
		case AbstractModMetadata.TYPE_BUILTIN:
		default:
			typePrefix = "";
		}

		return String.format("%s'%s' (%s)", typePrefix, candidate.getMetadata().getName(), candidate.getId());
	}

	private static String getVersion(ModCandidateImpl candidate) {
		return candidate.getVersion().getFriendlyString();
	}

	private static String getVersions(Collection<ModCandidateImpl> candidates) {
		return candidates.stream().map(ResultAnalyzer::getVersion).collect(Collectors.joining("/"));
	}

	private static String formatVersionRequirements(Collection<VersionInterval> intervals) {
		List<String> ret = new ArrayList<>();

		for (VersionInterval interval : intervals) {
			String str;

			if (interval == null) {
				// empty interval, skip
				continue;
			} else if (interval.getMin() == null) {
				if (interval.getMax() == null) {
					return Localization.format("resolution.version.any");
				} else if (interval.isMaxInclusive()) {
					str = Localization.format("resolution.version.lessEqual", interval.getMax());
				} else {
					str = Localization.format("resolution.version.less", interval.getMax());
				}
			} else if (interval.getMax() == null) {
				if (interval.isMinInclusive()) {
					str = Localization.format("resolution.version.greaterEqual", interval.getMin());
				} else {
					str = Localization.format("resolution.version.greater", interval.getMin());
				}
			} else if (interval.getMin().equals(interval.getMax())) {
				if (interval.isMinInclusive() && interval.isMaxInclusive()) {
					str = Localization.format("resolution.version.equal", interval.getMin());
				} else {
					// empty interval, skip
					continue;
				}
			} else if (isWildcard(interval, 0)) { // major.x wildcard
				SemanticVersion version = (SemanticVersion) interval.getMin();
				str = Localization.format("resolution.version.major", version.getVersionComponent(0));
			} else if (isWildcard(interval, 1)) { // major.minor.x wildcard
				SemanticVersion version = (SemanticVersion) interval.getMin();
				str = Localization.format("resolution.version.majorMinor", version.getVersionComponent(0), version.getVersionComponent(1));
			} else {
				String key = String.format("resolution.version.rangeMin%sMax%s",
						(interval.isMinInclusive() ? "Inc" : "Exc"),
						(interval.isMaxInclusive() ? "Inc" : "Exc"));
				str = Localization.format(key, interval.getMin(), interval.getMax());
			}

			ret.add(str);
		}

		if (ret.isEmpty()) {
			return Localization.format("resolution.version.none");
		} else {
			return formatEnumeration(ret, false);
		}
	}

	/**
	 * Determine whether an interval can be represented by a .x wildcard version string.
	 *
	 * <p>Example: [1.2.0-,1.3.0-) is the same as 1.2.x (incrementedComponent=1)
	 */
	private static boolean isWildcard(VersionInterval interval, int incrementedComponent) {
		if (interval == null || interval.getMin() == null || interval.getMax() == null // not an interval with lower+upper bounds
				|| !interval.isMinInclusive() || interval.isMaxInclusive() // not an [a,b) interval
				|| !interval.isSemantic()) {
			return false;
		}

		SemanticVersion min = (SemanticVersion) interval.getMin();
		SemanticVersion max = (SemanticVersion) interval.getMax();

		// min and max need to use the empty prerelease (a.b.c-)
		if (!"".equals(min.getPrereleaseKey().orElse(null))
				|| !"".equals(max.getPrereleaseKey().orElse(null))) {
			return false;
		}

		// max needs to be min + 1 for the covered component
		if (max.getVersionComponent(incrementedComponent) != min.getVersionComponent(incrementedComponent) + 1) {
			return false;
		}

		for (int i = incrementedComponent + 1, m = Math.max(min.getVersionComponentCount(), max.getVersionComponentCount()); i < m; i++) {
			// all following components need to be 0
			if (min.getVersionComponent(i) != 0 || max.getVersionComponent(i) != 0) {
				return false;
			}
		}

		return true;
	}

	private static String formatEnumeration(Collection<?> elements, boolean isAnd) {
		String keyPrefix = isAnd ? "enumerationAnd." : "enumerationOr.";
		Iterator<?> it = elements.iterator();

		switch (elements.size()) {
		case 0: return "";
		case 1: return Objects.toString(it.next());
		case 2: return Localization.format(keyPrefix+"2", it.next(), it.next());
		case 3: return Localization.format(keyPrefix+"3", it.next(), it.next(), it.next());
		}

		String ret = Localization.format(keyPrefix+"nPrefix", it.next());

		do {
			Object next = it.next();
			ret = Localization.format(it.hasNext() ? keyPrefix+"n" : keyPrefix+"nSuffix", ret, next);
		} while (it.hasNext());

		return ret;
	}
}
