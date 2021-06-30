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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.api.metadata.version.VersionPredicate.PredicateTerm;

final class ResultAnalyzer {
	static String gatherErrors(ModSolver.Result result, Map<String, ModCandidate> selectedMods, Map<String, List<ModCandidate>> modsById) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			for (Explanation explanation : result.reason) {
				assert explanation.error.isDependencyError;

				switch (explanation.error) {
				case HARD_DEP_NO_CANDIDATE:
					addErrorToList(explanation.mod, explanation.dep, (ModCandidate) null, pw);
					break;
				case HARD_DEP_INCOMPATIBLE_PRESELECTED:
					addErrorToList(explanation.mod, explanation.dep, selectedMods.get(explanation.dep.getModId()), pw);
					break;
				case PRESELECT_HARD_DEP:
				case HARD_DEP:
					addErrorToList(explanation.mod, explanation.dep, modsById.get(explanation.dep.getModId()), pw);
					break;
				case PRESELECT_NEG_HARD_DEP:
				case NEG_HARD_DEP:
					break;
				default:
					// ignore
				}
			}
		}

		return sw.toString();
	}

	static String gatherWarnings(List<ModCandidate> uniqueSelectedMods, Map<String, ModCandidate> selectedMods) {
		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			for (ModCandidate mod : uniqueSelectedMods) {
				for (ModDependency dep : mod.getDependencies()) {
					ModCandidate depMod;

					switch (dep.getKind()) {
					case RECOMMENDS:
						depMod = selectedMods.get(dep.getModId());

						if (depMod == null || !dep.matches(depMod.getVersion())) {
							addErrorToList(mod, dep, depMod, pw);
						}

						break;
					case CONFLICTS:
						depMod = selectedMods.get(dep.getModId());

						if (depMod != null && dep.matches(depMod.getVersion())) {
							addErrorToList(mod, dep, depMod, pw);
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

	private static void addErrorToList(ModCandidate mod, ModDependency dep, ModCandidate match, PrintWriter pw) {
		addErrorToList(mod, dep, match != null ? Collections.singletonList(match) : Collections.emptyList(), pw);
	}

	private static void addErrorToList(ModCandidate mod, ModDependency dep, List<ModCandidate> matches, PrintWriter pw) {
		if (matches == null) matches = Collections.emptyList();

		matches = matches.stream().filter(m -> dep.matches(m.getVersion())).collect(Collectors.toList());

		String errorType;

		switch (dep.getKind()) {
		case DEPENDS: errorType = "requires"; break;
		case RECOMMENDS: errorType = "recommends"; break;
		case BREAKS: errorType = "is incompatible with"; break;
		case CONFLICTS: errorType = "conflicts with"; break;
		default: throw new IllegalStateException("unknown dep kind: "+dep.getKind());
		}

		pw.printf("\n - Mod %s %s %s of mod %s, ",
				getCandidateName(mod), errorType, getDependencyVersionRequirements(dep),
				(matches.isEmpty() ? dep.getModId() : getCandidateName(matches.get(0))));

		if (matches.isEmpty()) {
			appendMissingDependencyError(dep, pw);
		} else if (dep.getKind().isPositive()) {
			appendUnsatisfiedDependencyError(dep, matches, pw);
		} else if (dep.getKind() == Kind.CONFLICTS) {
			appendConflictError(mod, matches, pw);
		} else {
			appendBreakingError(mod, matches, pw);
		}

		if (matches != null) {
			appendJiJInfo(matches, pw);
		}
	}

	private static void appendMissingDependencyError(ModDependency dependency, PrintWriter pw) {
		pw.printf("which is missing!\n\t - You %s install %s of %s.",
				(dependency.getKind().isSoft() ? "should" : "need to"),
				getDependencyVersionRequirements(dependency),
				dependency.getModId());
	}

	private static void appendUnsatisfiedDependencyError(ModDependency dependency, List<ModCandidate> matches, PrintWriter pw) {
		pw.printf("but a non-matching version is present: %s!\n\t - You must %s %s of %s.",
				getCandidateFriendlyVersions(matches),
				(dependency.getKind().isSoft() ? "should" : "need to"),
				getDependencyVersionRequirements(dependency),
				getCandidateName(matches.get(0)));
	}

	private static void appendConflictError(ModCandidate candidate, List<ModCandidate> matches, PrintWriter pw) {
		final String depCandidateVer = getCandidateFriendlyVersions(matches);

		pw.printf("but a matching version is present: %s!\n"
				+ "\t - While this won't prevent you from starting the game,  the developer(s) of %s have found that "
				+ "version %s of %s conflicts with their mod.\n"
				+ "\t - It is recommended to remove one of the mods.",
				depCandidateVer, getCandidateName(candidate), depCandidateVer, getCandidateName(matches.get(0)));
	}

	private static void appendBreakingError(ModCandidate candidate, List<ModCandidate> matches, PrintWriter pw) {
		final String depCandidateVer = getCandidateFriendlyVersions(matches);

		pw.printf("but a matching version is present: %s!\n"
				+ "\t - The developer(s) of %s have found that version %s of %s critically conflicts with their mod.\n"
				+ "\t - You need to remove one of the mods.",
				depCandidateVer, getCandidateName(candidate), depCandidateVer, getCandidateName(matches.get(0)));
	}

	private static void appendJiJInfo(List<ModCandidate> mods, PrintWriter pw) {
		for (ModCandidate mod : mods) {
			if (mod.getMinNestLevel() < 1) {
				pw.printf("\n\t - Mod %s v%s is being loaded from the mods directory.",
						getCandidateName(mod), getCandidateFriendlyVersion(mod));
				return;
			}

			List<ModCandidate> path = new ArrayList<>();
			ModCandidate cur = mod;

			do {
				for (ModCandidate parent : cur.getParentMods()) {
					if (parent.getMinNestLevel() < cur.getMinNestLevel()) {
						path.add(parent);
						cur = parent;
						break;
					}
				}
			} while (cur.getMinNestLevel() > 0);

			StringBuilder pathSb = new StringBuilder();

			for (int i = path.size() - 1; i >= 0; i--) {
				ModCandidate m = path.get(i);

				if (pathSb.length() > 0) pathSb.append(" -> ");
				pathSb.append(String.format("%s v%s", getCandidateName(m), getCandidateFriendlyVersion(m)));
			}

			// now we have the proper data, yay
			pw.printf("\n\t - Mod %s v%s is being provided through e.g. %s.",
					getCandidateName(mod), getCandidateFriendlyVersion(mod), pathSb);
		}
	}

	private static String getCandidateName(ModCandidate candidate) {
		return String.format("'%s' (%s)", candidate.getMetadata().getName(), candidate.getId());
	}

	private static String getCandidateFriendlyVersion(ModCandidate candidate) {
		return candidate.getVersion().getFriendlyString();
	}

	private static String getCandidateFriendlyVersions(Collection<ModCandidate> candidates) {
		return candidates.stream().map(ResultAnalyzer::getCandidateFriendlyVersion).collect(Collectors.joining("/"));
	}

	private static String getDependencyVersionRequirements(ModDependency dependency) {
		StringBuilder sb = new StringBuilder();

		Collection<VersionPredicate> predicates = dependency.getVersionRequirements();

		for (VersionPredicate predicate : predicates) {
			if (sb.length() > 0) sb.append(" or ");

			Collection<? extends PredicateTerm> terms = predicate.getTerms();
			if (terms.isEmpty()) return "any version";

			boolean useBrackets = predicates.size() > 1 && terms.size() > 1;
			if (useBrackets) sb.append('(');

			boolean first = true;

			for (PredicateTerm term : terms) {
				Version version = term.getReferenceVersion();
				VersionComparisonOperator operator;

				if (version instanceof SemanticVersion) {
					operator = term.getOperator();
				} else {
					operator = VersionComparisonOperator.EQUAL;
				}

				if (first) {
					first = false;
				} else {
					sb.append(" and ");
				}

				switch (operator) {
				case EQUAL:
					sb.append(String.format("version %s", version));
					break;
				case GREATER:
					sb.append(String.format("any version after %s", version));
					break;
				case LESS:
					sb.append(String.format("any version before %s", version));
					break;
				case GREATER_EQUAL:
					sb.append(String.format("version %s or later", version));
					break;
				case LESS_EQUAL:
					sb.append(String.format("version %s or earlier", version));
					break;
				case SAME_TO_NEXT_MAJOR:
					sb.append(String.format("version %d.x", ((SemanticVersion) version).getVersionComponent(0)));
					break;
				case SAME_TO_NEXT_MINOR: {
					SemanticVersion semVer = (SemanticVersion) version;
					sb.append(String.format("version %d.%d.x", semVer.getVersionComponent(0), semVer.getVersionComponent(1)));
					break;
				}
				default:
					throw new IllegalStateException(operator.toString());
				}
			}

			if (useBrackets) sb.append(')');
		}

		return sb.toString();
	}
}
