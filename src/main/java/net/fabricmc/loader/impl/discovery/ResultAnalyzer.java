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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.discovery.ModSolver.AddModVar;
import net.fabricmc.loader.impl.discovery.ModSolver.InactiveReason;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.util.Localization;
import net.fabricmc.loader.impl.util.StringUtil;

final class ResultAnalyzer {
	static String gatherErrors(ModSolver.Result result, Map<String, ModCandidate> selectedMods, Map<String, List<ModCandidate>> modsById,
			Map<String, Set<ModCandidate>> envDisabledMods, EnvType envType) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			String prefix = "";
			boolean suggestFix = true;

			if (result.fix != null) {
				pw.printf("\n%s", Localization.format("resolution.solutionHeader"));

				for (AddModVar mod : result.fix.modsToAdd) {
					if (envDisabledMods.containsKey(mod.getId())) {
						String envKey = String.format("environment.%s", envType.name().toLowerCase(Locale.ENGLISH));

						pw.printf("\n\t - %s", Localization.format("resolution.solution.addModEnvDisabled",
								mod.getId(),
								mod.getVersion().getFriendlyString(),
								Localization.format(envKey)));
					} else {
						pw.printf("\n\t - %s", Localization.format("resolution.solution.addMod", mod.getId(), mod.getVersion().getFriendlyString()));
					}
				}

				for (ModCandidate mod : result.fix.modsToRemove) {
					pw.printf("\n\t - %s", Localization.format("resolution.solution.removeMod", getName(mod), getVersion(mod), mod.getLocalPath()));
				}

				for (Entry<AddModVar, List<ModCandidate>> entry : result.fix.modReplacements.entrySet()) {
					AddModVar newMod = entry.getKey();
					List<ModCandidate> oldMods = entry.getValue();
					List<String> oldModEntries = new ArrayList<>(oldMods.size());

					for (ModCandidate m : oldMods) {
						if (m.hasPath() && !m.isBuiltin()) {
							oldModEntries.add(Localization.format("resolution.solution.replaceMod.oldMod", getName(m), getVersion(m), m.getLocalPath()));
						} else {
							oldModEntries.add(Localization.format("resolution.solution.replaceMod.oldModNoPath", getName(m), getVersion(m)));
						}
					}

					String newModName = newMod.getId();
					ModCandidate alt = selectedMods.get(newMod.getId());

					if (alt != null) {
						newModName = getName(alt);
					} else {
						List<ModCandidate> alts = modsById.get(newMod.getId());
						if (alts != null && !alts.isEmpty()) newModName = getName(alts.get(0));
					}

					pw.printf("\n\t - %s", Localization.format("resolution.solution.replaceMod", String.join(", ", oldModEntries), newModName, newMod.getVersion().getFriendlyString()));
				}

				pw.printf("\n%s", Localization.format("resolution.depListHeader"));
				prefix = "\t";
				suggestFix = false;
			}

			List<ModCandidate> matches = new ArrayList<>();

			for (Explanation explanation : result.reason) {
				assert explanation.error.isDependencyError;

				ModDependency dep = explanation.dep;
				ModCandidate selected = selectedMods.get(dep.getModId());

				if (selected != null) {
					matches.add(selected);
				} else {
					List<ModCandidate> candidates = modsById.get(dep.getModId());
					if (candidates != null) matches.addAll(candidates);
				}

				addErrorToList(explanation.mod, explanation.dep, matches, envDisabledMods.containsKey(dep.getModId()), suggestFix, prefix, pw);
				matches.clear();
			}

			if (result.fix != null && !result.fix.inactiveMods.isEmpty()) {
				pw.printf("\n%s", Localization.format("resolution.inactiveMods"));

				List<Map.Entry<ModCandidate, InactiveReason>> entries = new ArrayList<>(result.fix.inactiveMods.entrySet());

				// sort by root, id, version
				entries.sort(new Comparator<Map.Entry<ModCandidate, ?>>() {
					@Override
					public int compare(Entry<ModCandidate, ?> o1, Entry<ModCandidate, ?> o2) {
						ModCandidate a = o1.getKey();
						ModCandidate b = o2.getKey();

						if (a.isRoot() != b.isRoot()) {
							return a.isRoot() ? -1 : 1;
						}

						int cmp = a.getId().compareTo(b.getId());
						if (cmp != 0) return cmp;

						return a.getVersion().compareTo(b.getVersion());
					}
				});

				for (Map.Entry<ModCandidate, InactiveReason> entry : entries) {
					ModCandidate mod = entry.getKey();
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

	static String gatherWarnings(List<ModCandidate> uniqueSelectedMods, Map<String, ModCandidate> selectedMods,
			Map<String, Set<ModCandidate>> envDisabledMods, EnvType envType) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			for (ModCandidate mod : uniqueSelectedMods) {
				for (ModDependency dep : mod.getDependencies()) {
					ModCandidate depMod;

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

	private static List<ModCandidate> toList(ModCandidate mod) {
		return mod != null ? Collections.singletonList(mod) : Collections.emptyList();
	}

	private static void addErrorToList(ModCandidate mod, ModDependency dep, List<ModCandidate> matches, boolean presentForOtherEnv, boolean suggestFix, String prefix, PrintWriter pw) {
		Object[] args = new Object[] {
				getName(mod),
				(matches.isEmpty() ? dep.getModId() : getName(matches.get(0))),
				getDependencyVersionRequirements(dep),
				getVersions(matches),
				matches.size()
		};

		String reason;

		if (!matches.isEmpty()) {
			reason = "invalid";
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

		for (ModCandidate m : matches) {
			appendJijInfo(m, prefix, true, pw);
		}
	}

	private static void appendJijInfo(ModCandidate mod, String prefix, boolean mentionMod, PrintWriter pw) {
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

			List<ModCandidate> paths = new ArrayList<>();
			paths.add(mod);

			ModCandidate cur = mod;

			do {
				ModCandidate best = null;
				int maxDiff = 0;

				for (ModCandidate parent : cur.getParentMods()) {
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
				ModCandidate m = paths.get(i);

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

	private static String getName(ModCandidate candidate) {
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

	private static String getVersion(ModCandidate candidate) {
		return candidate.getVersion().getFriendlyString();
	}

	private static String getVersions(Collection<ModCandidate> candidates) {
		return candidates.stream().map(ResultAnalyzer::getVersion).collect(Collectors.joining("/"));
	}

	private static String getDependencyVersionRequirements(ModDependency dependency) {
		StringBuilder sb = new StringBuilder();

		Collection<VersionPredicate> predicates = dependency.getVersionRequirements();

		for (VersionPredicate predicate : predicates) {
			if (sb.length() > 0) sb.append(String.format(" %s ", Localization.format("resolution.version.or")));

			VersionInterval interval = predicate.getInterval();

			if (interval == null) {
				// empty interval, skip
			} else if (interval.getMin() == null) {
				if (interval.getMax() == null) {
					return Localization.format("resolution.version.any");
				} else if (interval.isMaxInclusive()) {
					sb.append(Localization.format("resolution.version.lessEqual", interval.getMax()));
				} else {
					sb.append(Localization.format("resolution.version.less", interval.getMax()));
				}
			} else if (interval.getMax() == null) {
				if (interval.isMinInclusive()) {
					sb.append(Localization.format("resolution.version.greaterEqual", interval.getMin()));
				} else {
					sb.append(Localization.format("resolution.version.greater", interval.getMin()));
				}
			} else if (interval.getMin().equals(interval.getMax())) {
				if (interval.isMinInclusive() && interval.isMaxInclusive()) {
					sb.append(Localization.format("resolution.version.equal", interval.getMin()));
				} else {
					// empty interval, skip
				}
			} else if (isWildcard(interval, 0)) { // major.x wildcard
				SemanticVersion version = (SemanticVersion) interval.getMin();
				sb.append(Localization.format("resolution.version.major", version.getVersionComponent(0)));
			} else if (isWildcard(interval, 1)) { // major.minor.x wildcard
				SemanticVersion version = (SemanticVersion) interval.getMin();
				sb.append(Localization.format("resolution.version.majorMinor", version.getVersionComponent(0), version.getVersionComponent(1)));
			} else {
				String key = String.format("resolution.version.rangeMin%sMax%s",
						(interval.isMinInclusive() ? "Inc" : "Exc"),
						(interval.isMaxInclusive() ? "Inc" : "Exc"));
				sb.append(Localization.format(key, interval.getMin(), interval.getMax()));
			}
		}

		if (sb.length() == 0) {
			return Localization.format("resolution.version.none");
		} else {
			return sb.toString();
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

		for (int i = incrementedComponent + 1; i < 3; i++) {
			// all following components need to be 0
			if (min.getVersionComponent(i) != 0 || max.getVersionComponent(i) != 0) {
				return false;
			}
		}

		return true;
	}
}
