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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.util.sat4j.core.VecInt;
import net.fabricmc.loader.util.sat4j.minisat.SolverFactory;
import net.fabricmc.loader.util.sat4j.specs.ContradictionException;
import net.fabricmc.loader.util.sat4j.specs.IProblem;
import net.fabricmc.loader.util.sat4j.specs.ISolver;
import net.fabricmc.loader.util.sat4j.specs.IVecInt;
import net.fabricmc.loader.util.sat4j.specs.TimeoutException;

public class ModResolver {
	public ModResolver() {
	}

	public Map<String, ModCandidate> resolve(Map<String, ModCandidateSet> candidatesById) throws ModResolutionException {
		long time2 = System.currentTimeMillis();
		Map<String, ModCandidate> result = findCompatibleSet(candidatesById);

		long time3 = System.currentTimeMillis();
		Log.debug(LogCategory.RESOLUTION, "Mod resolution time: " + (time3 - time2) + "ms");

		for (ModCandidate candidate : result.values()) {
			if (candidate.getInfo().getSchemaVersion() < ModMetadataParser.LATEST_VERSION) {
				Log.warn(LogCategory.METADATA, "Mod ID %s uses outdated schema version: %d < %d", candidate.getInfo().getId(), candidate.getInfo().getSchemaVersion(), ModMetadataParser.LATEST_VERSION);
			}

			candidate.getInfo().emitFormatWarnings();
		}

		return result;
	}

	// TODO: Find a way to sort versions of mods by suggestions and conflicts (not crucial, though)
	@SuppressWarnings("unchecked")
	private Map<String, ModCandidate> findCompatibleSet(Map<String, ModCandidateSet> modCandidateSetMap) throws ModResolutionException {
		// First, map all ModCandidateSets to Set<ModCandidate>s.
		boolean isAdvanced = false;
		Map<String, List<ModCandidate>> modCandidateMap = new HashMap<>();
		Set<String> mandatoryMods = new HashSet<>();

		for (ModCandidateSet mcs : modCandidateSetMap.values()) {
			Collection<ModCandidate> s = mcs.toSortedSet();
			modCandidateMap.computeIfAbsent(mcs.getModId(), i -> new ArrayList<>()).addAll(s);

			for (String modProvide : mcs.getModProvides()) {
				modCandidateMap.computeIfAbsent(modProvide, i -> new ArrayList<>()).addAll(s);
			}

			isAdvanced |= (s.size() > 1) || (s.iterator().next().getDepth() > 0);

			if (mcs.isUserProvided()) {
				mandatoryMods.add(mcs.getModId());
			}
		}

		Map<String, ModCandidate> result;

		if (!isAdvanced) {
			result = new HashMap<>();

			for (String s : modCandidateMap.keySet()) {
				ModCandidate candidate = modCandidateMap.get(s).iterator().next();
				// if the candidate isn't actually just a provided alias, then put it on
				if (!candidate.getInfo().getProvides().contains(s)) result.put(s, candidate);
			}
		} else {
			// Inspired by http://0install.net/solver.html
			// probably also horrendously slow, for now

			// Map all the ModCandidates to DIMACS-format positive integers.
			int varCount = 1;
			Map<ModCandidate, Integer> candidateIntMap = new HashMap<>();
			List<ModCandidate> intCandidateMap = new ArrayList<>(modCandidateMap.size() * 2);
			intCandidateMap.add(null);

			for (Collection<ModCandidate> m : modCandidateMap.values()) {
				for (ModCandidate candidate : m) {
					candidateIntMap.put(candidate, varCount++);
					intCandidateMap.add(candidate);
				}
			}

			ISolver solver = SolverFactory.newLight();
			solver.newVar(varCount);

			try {
				// Each mod needs to have at most one version.
				for (String id : modCandidateMap.keySet()) {
					IVecInt versionVec = toVecInt(modCandidateMap.get(id).stream().mapToInt(candidateIntMap::get));

					try {
						if (mandatoryMods.contains(id)) {
							solver.addExactly(versionVec, 1);
						} else {
							solver.addAtMost(versionVec, 1);
						}
					} catch (ContradictionException e) {
						throw new ModResolutionException(String.format("Could not resolve valid mod collection (at: adding mod %s)", id), e);
					}
				}

				for (ModCandidate mod : candidateIntMap.keySet()) {
					int modClauseId = candidateIntMap.get(mod);

					// Each mod's requirements must be satisfied, if it is to be present.
					// mod => ((a or b) AND (d or e))
					// \> not mod OR ((a or b) AND (d or e))
					// \> ((not mod OR a OR b) AND (not mod OR d OR e))

					for (ModDependency dep : mod.getInfo().getDepends()) {
						int[] matchingCandidates = modCandidateMap.getOrDefault(dep.getModId(), Collections.emptyList())
								.stream()
								.filter((c) -> dep.matches(c.getInfo().getVersion()))
								.mapToInt(candidateIntMap::get)
								.toArray();

						int[] clause = new int[matchingCandidates.length + 1];
						System.arraycopy(matchingCandidates, 0, clause, 0, matchingCandidates.length);
						clause[matchingCandidates.length] = -modClauseId;

						try {
							solver.addClause(new VecInt(clause));
						} catch (ContradictionException e) {
							//TODO: JiJ'd mods don't manage to throw this exception and instead fail silently
							throw new ModResolutionException(String.format("Could not find required mod: %s requires %s", mod.getInfo().getId(), dep), e);
						}
					}

					// Each mod's breaks must be NOT satisfied, if it is to be present.
					// mod => (not a AND not b AND not d AND not e))
					// \> not mod OR (not a AND not b AND not d AND not e)
					// \> (not mod OR not a) AND (not mod OR not b) ...

					for (ModDependency dep : mod.getInfo().getBreaks()) {
						int[] matchingCandidates = modCandidateMap.getOrDefault(dep.getModId(), Collections.emptyList())
								.stream()
								.filter((c) -> dep.matches(c.getInfo().getVersion()))
								.mapToInt(candidateIntMap::get)
								.toArray();

						try {
							for (int m : matchingCandidates) {
								solver.addClause(new VecInt(new int[] { -modClauseId, -m }));
							}
						} catch (ContradictionException e) {
							throw new ModResolutionException(String.format("Found conflicting mods: %s conflicts with %s", mod.getInfo().getId(), dep), e);
						}
					}
				}

				//noinspection UnnecessaryLocalVariable
				IProblem problem = solver;
				IVecInt assumptions = new VecInt(modCandidateMap.size());

				for (String mod : modCandidateMap.keySet()) {
					int pos = assumptions.size();
					assumptions = assumptions.push(0);
					Collection<ModCandidate> candidates = modCandidateMap.get(mod);
					boolean satisfied = false;

					for (ModCandidate candidate : candidates) {
						assumptions.set(pos, candidateIntMap.get(candidate));

						if (problem.isSatisfiable(assumptions)) {
							satisfied = true;
							break;
						}
					}

					if (!satisfied) {
						if (mandatoryMods.contains(mod)) {
							throw new ModResolutionException("Could not resolve mod collection including mandatory mod '%s'", mod);
						} else {
							assumptions = assumptions.pop();
						}
					}
				}

				// TODO: better error message, have a way to figure out precisely which mods are causing this
				if (!problem.isSatisfiable(assumptions)) {
					throw new ModResolutionException("Could not resolve mod collection due to an unknown error");
				}

				int[] model = problem.model();
				result = new HashMap<>();

				for (int i : model) {
					if (i <= 0) {
						continue;
					}

					ModCandidate candidate = intCandidateMap.get(i);

					if (result.containsKey(candidate.getInfo().getId())) {
						throw new ModResolutionException("Duplicate ID '%s' after solving - wrong constraints?", candidate.getInfo().getId());
					} else {
						result.put(candidate.getInfo().getId(), candidate);
					}
				}
			} catch (TimeoutException e) {
				throw new ModResolutionException("Mod collection took too long to be resolved", e);
			}
		}

		// verify result: all mandatory mods
		Set<String> missingMods = new HashSet<>();

		for (String m : mandatoryMods) {
			if (!result.keySet().contains(m)) {
				missingMods.add(m);
			}
		}

		StringWriter errorsHardSw = new StringWriter();
		StringWriter errorsSoftSw = new StringWriter();

		try (PrintWriter errorsHardPw = new PrintWriter(errorsHardSw);
				PrintWriter errorsSoftPw = new PrintWriter(errorsSoftSw)) {
			if (!missingMods.isEmpty()) {
				errorsHardPw.printf("\n - Missing mods: %s", String.join(", ", missingMods));
			} else {
				// verify result: dependencies
				for (ModCandidate candidate : result.values()) {
					for (ModDependency dependency : candidate.getInfo().getDepends()) {
						addErrorToList(candidate, dependency, result, "requires", true, errorsHardPw);
					}

					for (ModDependency dependency : candidate.getInfo().getRecommends()) {
						addErrorToList(candidate, dependency, result, "recommends", true, errorsSoftPw);
					}

					for (ModDependency dependency : candidate.getInfo().getBreaks()) {
						addErrorToList(candidate, dependency, result, "is incompatible with", false, errorsHardPw);
					}

					for (ModDependency dependency : candidate.getInfo().getConflicts()) {
						addErrorToList(candidate, dependency, result, "conflicts with", false, errorsSoftPw);
					}

					Version version = candidate.getInfo().getVersion();
					List<Version> suspiciousVersions = new ArrayList<>();

					for (ModCandidate other : modCandidateMap.get(candidate.getInfo().getId())) {
						Version otherVersion = other.getInfo().getVersion();

						if (version instanceof Comparable && otherVersion instanceof Comparable && !version.equals(otherVersion)) {
							if (((Comparable<Version>) version).compareTo(otherVersion) == 0) {
								suspiciousVersions.add(otherVersion);
							}
						}
					}

					if (!suspiciousVersions.isEmpty()) {
						errorsSoftPw.printf("\n - Conflicting versions found for %s: used %s, also found %s",
								candidate.getInfo().getId(),
								version.getFriendlyString(),
								suspiciousVersions.stream().map(Version::getFriendlyString).collect(Collectors.joining(", ")));
					}
				}
			}
		}

		// print errors

		if (errorsHardSw.getBuffer().length() > 0) {
			throw new ModResolutionException("Errors were found!%s%s", errorsHardSw, errorsSoftSw);
		} else if (errorsSoftSw.getBuffer().length() > 0) {
			Log.warn(LogCategory.RESOLUTION, "Warnings were found! %s", errorsSoftSw);
		}

		return result;
	}

	private static IVecInt toVecInt(IntStream stream) {
		return new VecInt(stream.toArray());
	}

	private void addErrorToList(ModCandidate candidate, ModDependency dependency, Map<String, ModCandidate> result, String errorType, boolean cond, PrintWriter pw) {
		String depModId = dependency.getModId();

		List<String> errorList = new ArrayList<>();

		if (!ModDiscoverer.isModIdValid(depModId, errorList)) {
			pw.printf("\n - Mod %s %s %s, which has an invalid mod ID because:",
					getCandidateName(candidate), errorType, depModId);

			for (String error : errorList) {
				pw.printf("\n\t - It %s", error);
			}

			return;
		}

		ModCandidate depCandidate = result.get(depModId);

		// attempt searching provides
		if (depCandidate == null) {
			for (ModCandidate value : result.values()) {
				if (value.getInfo().getProvides().contains(depModId)) {
					if (FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
						Log.warn(LogCategory.METADATA, "Mod %s is using the provided alias %s in place of the real mod id %s. Please use the mod id instead of a provided alias.",
								candidate.getInfo().getId(), depModId, value.getInfo().getId());
					}

					depCandidate = value;
					break;
				}
			}
		}

		boolean isPresent = depCandidate != null && dependency.matches(depCandidate.getInfo().getVersion());

		if (isPresent != cond) {
			pw.printf("\n - Mod %s %s %s of mod %s, ",
					getCandidateName(candidate), errorType, getDependencyVersionRequirements(dependency),
					(depCandidate == null ? depModId : getCandidateName(depCandidate)));

			if (depCandidate == null) {
				appendMissingDependencyError(dependency, pw);
			} else if (cond) {
				appendUnsatisfiedDependencyError(dependency, depCandidate, pw);
			} else if (errorType.contains("conf")) {
				// CONFLICTS WITH
				appendConflictError(candidate, depCandidate, pw);
			} else {
				appendBreakingError(candidate, depCandidate, pw);
			}

			if (depCandidate != null) {
				appendJiJInfo(result, depCandidate, pw);
			}
		}
	}

	private void appendMissingDependencyError(ModDependency dependency, PrintWriter pw) {
		pw.printf("which is missing!\n\t - You must install %s of %s.",
				getDependencyVersionRequirements(dependency), dependency.getModId());
	}

	private void appendUnsatisfiedDependencyError(ModDependency dependency, ModCandidate depCandidate, PrintWriter pw) {
		pw.printf("but a non-matching version is present: %s!\n\t - You must install %s of %s.",
				getCandidateFriendlyVersion(depCandidate), getDependencyVersionRequirements(dependency), getCandidateName(depCandidate));
	}

	private void appendConflictError(ModCandidate candidate, ModCandidate depCandidate, PrintWriter pw) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);

		pw.printf("but a matching version is present: %s!\n"
				+ "\t - While this won't prevent you from starting the game,  the developer(s) of %s have found that "
				+ "version %s of %s conflicts with their mod.\n"
				+ "\t - It is heavily recommended to remove one of the mods.",
				depCandidateVer, getCandidateName(candidate), depCandidateVer, getCandidateName(depCandidate));
	}

	private void appendBreakingError(ModCandidate candidate, ModCandidate depCandidate, PrintWriter pw) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);

		pw.printf("but a matching version is present: %s!\n"
				+ "\t - The developer(s) of %s have found that version %s of %s critically conflicts with their mod.\n"
				+ "\t - You must remove one of the mods.",
				depCandidate.getInfo().getVersion(), getCandidateName(candidate), depCandidateVer, getCandidateName(depCandidate));
	}

	private void appendJiJInfo(Map<String, ModCandidate> result, ModCandidate candidate, PrintWriter pw) {
		if (candidate.getDepth() < 1) {
			pw.printf("\n\t - Mod %s v%s is being loaded from the user's mod directory.",
					getCandidateName(candidate), getCandidateFriendlyVersion(candidate));
			return;
		}

		URL originUrl = candidate.getOriginUrl();
		// step 1: try to find source mod's URL
		URL sourceUrl = null;

		try {
			for (Map.Entry<String, List<Path>> entry : ModDiscoverer.inMemoryCache.entrySet()) {
				for (Path path : entry.getValue()) {
					URL url = UrlUtil.asUrl(path.normalize());

					if (originUrl.equals(url)) {
						sourceUrl = new URL(entry.getKey());
						break;
					}
				}
			}
		} catch (UrlConversionException | MalformedURLException e) {
			e.printStackTrace();
		}

		if (sourceUrl == null) {
			pw.printf("\n\t - Mod %s v%s is being provided by <unknown mod>.",
					getCandidateName(candidate), getCandidateFriendlyVersion(candidate));
			return;
		}

		// step 2: try to find source mod candidate
		ModCandidate srcCandidate = null;

		for (Map.Entry<String, ModCandidate> entry : result.entrySet()) {
			if (sourceUrl.equals(entry.getValue().getOriginUrl())) {
				srcCandidate = entry.getValue();
				break;
			}
		}

		if (srcCandidate == null) {
			pw.printf("\n\t - Mod %s v%s is being provided by <unknown mod: %s>.",
					getCandidateName(candidate), getCandidateFriendlyVersion(candidate), sourceUrl);
			return;
		}

		// now we have the proper data, yay
		pw.printf("\n\t - Mod %s v%s is being provided by %s v%s.",
				getCandidateName(candidate), getCandidateFriendlyVersion(candidate), getCandidateName(srcCandidate), getCandidateFriendlyVersion(candidate));
	}

	private static String getCandidateName(ModCandidate candidate) {
		return String.format("'%s' (%s)", candidate.getInfo().getName(), candidate.getInfo().getId());
	}

	private static String getCandidateFriendlyVersion(ModCandidate candidate) {
		return candidate.getInfo().getVersion().getFriendlyString();
	}

	private static String getDependencyVersionRequirements(ModDependency dependency) {
		return dependency.getVersionRequirements().stream().map(predicate -> {
			String version = predicate.getVersion();
			String[] parts;

			switch (predicate.getType()) {
			case ANY:
				return "any version";
			case EQUALS:
				return "version " + version;
			case GREATER_THAN:
				return "any version after " + version;
			case LESSER_THAN:
				return "any version before " + version;
			case GREATER_THAN_OR_EQUAL:
				return "version " + version + " or later";
			case LESSER_THAN_OR_EQUAL:
				return "version " + version + " or earlier";
			case SAME_MAJOR:
				parts = version.split("\\.");

				for (int i = 1; i < parts.length; i++) {
					parts[i] = "x";
				}

				return "version " + String.join(".", parts);
			case SAME_MAJOR_AND_MINOR:
				parts = version.split("\\.");

				for (int i = 2; i < parts.length; i++) {
					parts[i] = "x";
				}

				return "version " + String.join(".", parts);
			default:
				return "unknown version"; // should be unreachable
			}
		}).collect(Collectors.joining(" or "));
	}
}
