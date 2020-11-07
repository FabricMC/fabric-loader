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

package net.fabricmc.loader.discovery;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.lib.gson.MalformedJsonException;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.metadata.NestedJarEntry;
import net.fabricmc.loader.metadata.ModMetadataParser;
import net.fabricmc.loader.metadata.ParseMetadataException;
import net.fabricmc.loader.util.FileSystemUtil;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.sat4j.pb.SolverFactory;
import net.fabricmc.loader.util.sat4j.pb.tools.DependencyHelper;
import net.fabricmc.loader.util.sat4j.pb.tools.INegator;
import net.fabricmc.loader.util.sat4j.specs.ContradictionException;
import net.fabricmc.loader.util.sat4j.specs.TimeoutException;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipError;

import static com.google.common.jimfs.Feature.FILE_CHANNEL;
import static com.google.common.jimfs.Feature.SECURE_DIRECTORY_STREAM;

public class ModResolver {
	// nested JAR store
	private static final FileSystem inMemoryFs = Jimfs.newFileSystem(
		"nestedJarStore",
		Configuration.builder(PathType.unix())
			.setRoots("/")
			.setWorkingDirectory("/")
			.setAttributeViews("basic")
			.setSupportedFeatures(SECURE_DIRECTORY_STREAM, FILE_CHANNEL)
			.build()
	);
	private static final Map<String, List<Path>> inMemoryCache = new ConcurrentHashMap<>();
	private static final Map<String, String> readableNestedJarPaths = new ConcurrentHashMap<>();
	private static final Pattern MOD_ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{1,63}");
	private static final Object launcherSyncObject = new Object();

	private final List<ModCandidateFinder> candidateFinders = new ArrayList<>();

	public ModResolver() {
	}

	public void addCandidateFinder(ModCandidateFinder f) {
		candidateFinders.add(f);
	}

	public static String getReadablePath(FabricLoader loader, ModCandidate c) {
		Path path;
		try {
			path = UrlUtil.asPath(c.getOriginUrl());
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}

		Path gameDir = loader.getGameDirectory().toPath().normalize();
		if (path.startsWith(gameDir)) {
			path = gameDir.relativize(path);
		}

		return readableNestedJarPaths.getOrDefault(c.getOriginUrl().toString(), path.toString());
	}

	// TODO: Find a way to sort versions of mods by suggestions and conflicts (not crucial, though)
	public Map<String, ModCandidate> findCompatibleSet(Logger logger, Map<String, ModCandidateSet> modCandidateSetMap) throws ModResolutionException {
		// First, map all ModCandidateSets to Set<ModCandidate>s.
		boolean isAdvanced = false;
		Map<String, Collection<ModCandidate>> modCandidateMap = new HashMap<>();
		Map<String, ModCandidate> mandatoryMods = new HashMap<>();

		List<ModResolutionException> errors = new ArrayList<>();

		for (ModCandidateSet mcs : modCandidateSetMap.values()) {
			try {
				Collection<ModCandidate> s = mcs.toSortedSet();
				modCandidateMap.put(mcs.getModId(), s);
				isAdvanced |= (s.size() > 1) || (s.iterator().next().getDepth() > 0);

				if (mcs.isUserProvided()) {
					mandatoryMods.put(mcs.getModId(), s.iterator().next());
				}
			} catch (ModResolutionException e) {
				errors.add(e);
			}
		}

		if (!errors.isEmpty()) {
			if (errors.size() == 1) {
				throw errors.get(0);
			}
			ModResolutionException ex = new ModResolutionException("Found " + errors.size() + " duplicated mandatory mods!");
			for (ModResolutionException error : errors) {
				ex.addSuppressed(error);
			}
			throw ex;
		}

		Map<String, ModCandidate> result;

		isAdvanced = true;

		if (!isAdvanced) {
			result = new HashMap<>();
			for (String s : modCandidateMap.keySet()) {
				result.put(s, modCandidateMap.get(s).iterator().next());
			}
		} else {
			Map<String, ModIdDefinition> modDefs = new HashMap<>();
			Map<ModCandidate, ModLoadOption> modToLoadOption = new HashMap<>();
			DependencyHelper<LoadOption, ModLink> helper = new DependencyHelper<>(SolverFactory.newLight());
			helper.setNegator(new LoadOptionNegator());

			try {

				for (Entry<String, Collection<ModCandidate>> entry : modCandidateMap.entrySet()) {
					String modId = entry.getKey();
					Collection<ModCandidate> candidates = entry.getValue();
					ModCandidate mandatedCandidate = mandatoryMods.get(modId);
					MandatoryModIdDefinition mandatedDefinition = null;

					if (mandatedCandidate != null) {
						ModLoadOption cOption = new ModLoadOption(mandatedCandidate, -1);
						modToLoadOption.put(mandatedCandidate, cOption);
						mandatedDefinition = new MandatoryModIdDefinition(cOption);
					}

					List<ModLoadOption> cOptions = new ArrayList<>();
					int index = 0;
					for (ModCandidate m : candidates) {
						if (m == mandatedCandidate) {
							continue;
						}

						ModLoadOption cOption = new ModLoadOption(m, candidates.size() == 1 ? -1 : index);
						modToLoadOption.put(m, cOption);
						helper.addToObjectiveFunction(cOption, 1000 - index++);
						cOptions.add(cOption);
					}

					ModIdDefinition def;
					ModLoadOption[] optionArray = cOptions.toArray(new ModLoadOption[0]);

					if (mandatedCandidate != null) {
						def = mandatedDefinition;
						if (optionArray.length > 0) {
							new OverridenModIdDefintion(mandatedDefinition, optionArray).put(helper);
						}
					} else {
						def = new OptionalModIdDefintion(modId, optionArray);
					}

					def.put(helper);
					modDefs.put(modId, def);
				}

				for (Entry<ModCandidate, ModLoadOption> entry : modToLoadOption.entrySet()) {
					ModCandidate mc = entry.getKey();
					ModLoadOption option = entry.getValue();

					for (ModDependency dep : mc.getInfo().getDepends()) {
						ModIdDefinition def = modDefs.get(dep.getModId());
						if (def == null) {
							def = new OptionalModIdDefintion(dep.getModId(), new ModLoadOption[0]);
							modDefs.put(dep.getModId(), def);
							def.put(helper);
						}

						new ModDep(option, dep, def).put(helper);
					}

					for (ModDependency conflict : mc.getInfo().getConflicts()) {
						ModIdDefinition def = modDefs.get(conflict.getModId());
						if (def == null) {
							def = new OptionalModIdDefintion(conflict.getModId(), new ModLoadOption[0]);
							modDefs.put(conflict.getModId(), def);

							def.put(helper);
						}

						for (ModLoadOption op : def.sources()) {
							new ModConflict(option, conflict, op).put(helper);
						}
					}
				}

			} catch (ContradictionException e) {
				// This shouldn't happen. But if it does it's a bit of a problem.
				throw new ModResolutionException(e);
			}

			// Resolving

			try {
				while (!helper.hasASolution()) {

					List<ModLink> why = new ArrayList<>(helper.why());

					Map<ModLoadOption, MandatoryModIdDefinition> roots = new HashMap<>();
					List<ModLink> causes = new ArrayList<>();

					// Find all of the problems
					for (Iterator<ModLink> iterator = why.iterator(); iterator.hasNext();) {
						ModLink link = iterator.next();
						if (link instanceof MandatoryModIdDefinition) {
							MandatoryModIdDefinition mandatoryMod = (MandatoryModIdDefinition) link;
							roots.put(mandatoryMod.candidate, mandatoryMod);
							iterator.remove();
						}
					}

					causes.addAll(why);

					ModResolutionException ex = describeError(roots, causes);
					if (ex == null) {
						StringBuilder sb = new StringBuilder("Unhandled error involving:");

						Map<String, Set<ModLoadOption>> mods = new HashMap<>();
						for (ModLink link : why) {
							if (link instanceof ModIdDefinition) {
								ModIdDefinition def = (ModIdDefinition) link;
								Set<ModLoadOption> set = mods.get(def.getModId());
								if (set == null) {
									mods.put(def.getModId(), set = new HashSet<>());
								}
								Collections.addAll(set, def.sources());
								causes.remove(link);
							} else if (link instanceof ModDep) {
								ModDep dep = (ModDep) link;
								mods.computeIfAbsent(dep.source.modId(), s -> new HashSet<>()).add(dep.source);
								Set<ModLoadOption> set = mods.get(dep.on.getModId());
								if (set == null) {
									mods.put(dep.on.getModId(), set = new HashSet<>());
								}
								Collections.addAll(set, dep.on.sources());
							} else if (link instanceof ModConflict) {
								ModConflict c = (ModConflict) link;
							}
						}

						for (Entry<String, Set<ModLoadOption>> entry : mods.entrySet()) {
							String modid = entry.getKey();
							Set<ModLoadOption> sources = entry.getValue();
							if (sources.isEmpty()) {
								sb.append("\nx unknown mod '" + modid + "'");
							} else {
								if (mandatoryMods.containsKey(modid)) {
									sb.append("\nmandatory mod '" + modid + "'");
								} else {
									sb.append("\noptional mod '" + modid + "'");
								}
								for (ModLoadOption src : sources) {
									sb.append("\n\t - " + src.getSpecificInfo());
								}
							}
						}

						for (ModLink link : causes) {
							sb.append("\n" + link);
						}
						ex = new ModResolutionException(sb.toString());
					}

					errors.add(ex);

					if (causes.isEmpty()) {
						break;
					} else {

						boolean removedAny = false;

						// Remove dependences and conflicts first
						for (ModLink link : causes) {
							if (link instanceof ModDep || link instanceof ModConflict) {
								if (helper.removeConstraint(link)) {
									removedAny = true;
									break;
								}
							}
						}

						// If that failed... try removing anything else
						if (!removedAny) {
							for (ModLink link : causes) {
								if (helper.removeConstraint(link)) {
									removedAny = true;
									break;
								}
							}
						}

						// If that failed... stop finding more errors
						if (!removedAny) {
							break;
						}
					}
				}

				if (!errors.isEmpty()) {
					if (errors.size() == 1) {
						throw errors.get(0);
					}
					ModResolutionException ex = new ModResolutionException("Found " + errors.size() + " errors while resolving mods!");
					for (ModResolutionException error : errors) {
						ex.addSuppressed(error);
					}
					throw ex;
				}

			} catch (TimeoutException e) {
				throw new ModResolutionException("Mod collection took too long to be resolved", e);
			}

			Collection<LoadOption> solution = helper.getASolution();
			result = new HashMap<>();

			for (LoadOption option : solution) {
				
				boolean negated = option instanceof NegatedLoadOption;
				if (negated) {
					option = ((NegatedLoadOption) option).not;
				}

				if (option instanceof ModLoadOption) {
					if (!negated) {
						ModLoadOption modOption = (ModLoadOption) option;

						ModCandidate previous = result.put(modOption.modId(), modOption.candidate);
						if (previous != null) {
							throw new ModResolutionException("Duplicate result ModCandidate for " + modOption.modId() + " - something has gone wrong internally!");
						}
					}
				} else {
					throw new IllegalStateException("Unknown LoadOption " + option);
				}
			}
		}

		// verify result: all mandatory mods
		Set<String> missingMods = new HashSet<>();
		for (String m : mandatoryMods.keySet()) {
			if (!result.keySet().contains(m)) {
				missingMods.add(m);
			}
		}

		StringBuilder errorsHard = new StringBuilder();
		StringBuilder errorsSoft = new StringBuilder();

		// TODO: Convert to new error syntax
		if (!missingMods.isEmpty()) {
			errorsHard.append("\n - Missing mods: ").append(String.join(", ", missingMods));
		} else {
			// verify result: dependencies
			for (ModCandidate candidate : result.values()) {
				for (ModDependency dependency : candidate.getInfo().getDepends()) {
					addErrorToList(candidate, dependency, result, errorsHard, "requires", true);
				}

				for (ModDependency dependency : candidate.getInfo().getRecommends()) {
					addErrorToList(candidate, dependency, result, errorsSoft, "recommends", true);
				}

				for (ModDependency dependency : candidate.getInfo().getBreaks()) {
					addErrorToList(candidate, dependency, result, errorsHard, "is incompatible with", false);
				}

				for (ModDependency dependency : candidate.getInfo().getConflicts()) {
					addErrorToList(candidate, dependency, result, errorsSoft, "conflicts with", false);
				}

				Version version = candidate.getInfo().getVersion();
				List<Version> suspiciousVersions = new ArrayList<>();

				for (ModCandidate other : modCandidateMap.get(candidate.getInfo().getId())) {
					Version otherVersion = other.getInfo().getVersion();
					if (version instanceof Comparable && otherVersion instanceof Comparable && !version.equals(otherVersion)) {
						//noinspection unchecked
						if (((Comparable) version).compareTo(otherVersion) == 0) {
							suspiciousVersions.add(otherVersion);
						}
					}
				}

				if (!suspiciousVersions.isEmpty()) {
					errorsSoft.append("\n - Conflicting versions found for ")
						.append(candidate.getInfo().getId())
						.append(": used ")
						.append(version.getFriendlyString())
						.append(", also found ")
						.append(suspiciousVersions.stream().map(Version::getFriendlyString).collect(Collectors.joining(", ")));
				}
			}
		}

		// print errors
		String errHardStr = errorsHard.toString();
		String errSoftStr = errorsSoft.toString();

		if (!errSoftStr.isEmpty()) {
			logger.warn("Warnings were found! " + errSoftStr);
		}

		if (!errHardStr.isEmpty()) {
			throw new ModResolutionException("Errors were found!" + errHardStr + errSoftStr);
		}

		return result;
	}

	// TODO: Convert all these methods to new error syntax
	private void addErrorToList(ModCandidate candidate, ModDependency dependency, Map<String, ModCandidate> result, StringBuilder errors, String errorType, boolean cond) {
		String depModId = dependency.getModId();

		List<String> errorList = new ArrayList<>();

		if (!isModIdValid(depModId, errorList)) {
			errors.append("\n - Mod ").append(getCandidateName(candidate)).append(" ").append(errorType).append(" ")
					.append(depModId).append(", which has an invalid mod ID because:");

			for (String error : errorList) {
				errors.append("\n\t - It ").append(error);
			}

			return;
		}

		ModCandidate depCandidate = result.get(depModId);
		boolean isPresent = depCandidate != null && dependency.matches(depCandidate.getInfo().getVersion());

		if (isPresent != cond) {
			errors.append("\n - Mod ").append(getCandidateName(candidate)).append(" ").append(errorType).append(" ")
					.append(getDependencyVersionRequirements(dependency)).append(" of mod ")
					.append(depCandidate == null ? depModId : getCandidateName(depCandidate)).append(", ");
			if (depCandidate == null) {
				appendMissingDependencyError(errors, dependency);
			} else if (cond) {
				appendUnsatisfiedDependencyError(errors, dependency, depCandidate);
			} else if (errorType.contains("conf")) {
				// CONFLICTS WITH
				appendConflictError(errors, candidate, depCandidate);
			} else {
				appendBreakingError(errors, candidate, depCandidate);
			}
			if (depCandidate != null) {
				appendJiJInfo(errors, result, depCandidate);
			}
		}
	}

	private void appendMissingDependencyError(StringBuilder errors, ModDependency dependency) {
		errors.append("which is missing!");
		errors.append("\n\t - You must install ").append(getDependencyVersionRequirements(dependency)).append(" of ")
				.append(dependency.getModId()).append(".");
	}

	private void appendUnsatisfiedDependencyError(StringBuilder errors, ModDependency dependency, ModCandidate depCandidate) {
		errors.append("but a non-matching version is present: ").append(getCandidateFriendlyVersion(depCandidate)).append("!");
		errors.append("\n\t - You must install ").append(getDependencyVersionRequirements(dependency)).append(" of ")
				.append(getCandidateName(depCandidate)).append(".");
	}

	private void appendConflictError(StringBuilder errors, ModCandidate candidate, ModCandidate depCandidate) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);
		errors.append("but a matching version is present: ").append(depCandidateVer).append("!");
		errors.append("\n\t - While this won't prevent you from starting the game,");
		errors.append(" the developer(s) of ").append(getCandidateName(candidate));
		errors.append(" have found that version ").append(depCandidateVer).append(" of ").append(getCandidateName(depCandidate));
		errors.append(" conflicts with their mod.");
		errors.append("\n\t - It is heavily recommended to remove one of the mods.");
	}

	private void appendBreakingError(StringBuilder errors, ModCandidate candidate, ModCandidate depCandidate) {
		final String depCandidateVer = getCandidateFriendlyVersion(depCandidate);
		errors.append("but a matching version is present: ").append(depCandidate.getInfo().getVersion()).append("!");
		errors.append("\n\t - The developer(s) of ").append(getCandidateName(candidate));
		errors.append(" have found that version ").append(depCandidateVer).append(" of ").append(getCandidateName(depCandidate));
		errors.append(" critically conflicts with their mod.");
		errors.append("\n\t - You must remove one of the mods.");
	}

	private void appendJiJInfo(StringBuilder errors, Map<String, ModCandidate> result, ModCandidate candidate) {
		if (candidate.getDepth() < 1) {
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being loaded from the user's mod directory.");
			return;
		}
		URL originUrl = candidate.getOriginUrl();
		// step 1: try to find source mod's URL
		URL sourceUrl = null;
		try {
			for (Map.Entry<String, List<Path>> entry : inMemoryCache.entrySet()) {
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
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being provided by <unknown mod>.");
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
			errors.append("\n\t - Mod ").append(getCandidateName(candidate))
					.append(" v").append(getCandidateFriendlyVersion(candidate))
					.append(" is being provided by <unknown mod: ")
					.append(sourceUrl).append(">.");
			return;
		}
		// now we have the proper data, yay
		errors.append("\n\t - Mod ").append(getCandidateName(candidate))
				.append(" v").append(getCandidateFriendlyVersion(candidate))
				.append(" is being provided by ").append(getCandidateName(srcCandidate))
				.append(" v").append(getCandidateFriendlyVersion(candidate))
				.append('.');
	}

	private static String getCandidateName(ModCandidate candidate) {
		return "'" + candidate.getInfo().getName() + "' (" + candidate.getInfo().getId() + ")";
	}

	private static String getCandidateFriendlyVersion(ModCandidate candidate) {
		return candidate.getInfo().getVersion().getFriendlyString();
	}

	private static String getDependencyVersionRequirements(ModDependency dependency) {
		return dependency.getVersionRequirements().stream().map(predicate -> {
			String version = predicate.getVersion();
			String[] parts;
			switch(predicate.getType()) {
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

	/** @param errorList The list of errors. The returned list of errors all need to be prefixed with "it " in order to make sense. */
	private static boolean isModIdValid(String modId, List<String> errorList) {
		// A more useful error list for MOD_ID_PATTERN
		if (modId.isEmpty()) {
			errorList.add("is empty!");
			return false;
		}

		if (modId.length() == 1) {
			errorList.add("is only a single character! (It must be at least 2 characters long)!");
		} else if (modId.length() > 64) {
			errorList.add("has more than 64 characters!");
		}

		char first = modId.charAt(0);

		if (first < 'a' || first > 'z') {
			errorList.add("starts with an invalid character '" + first + "' (it must be a lowercase a-z - uppercase isn't allowed anywhere in the ID)");
		}

		Set<Character> invalidChars = null;

		for (int i = 1; i < modId.length(); i++) {
			char c = modId.charAt(i);

			if (c == '-' || c == '_' || ('0' <= c && c <= '9') || ('a' <= c && c <= 'z')) {
				continue;
			}

			if (invalidChars == null) {
				invalidChars = new HashSet<>();
			}

			invalidChars.add(c);
		}

		if (invalidChars != null) {
			StringBuilder error = new StringBuilder("contains invalid characters: ");
			error.append(invalidChars.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", ")));
			errorList.add(error.append("!").toString());
		}

		assert errorList.isEmpty() == MOD_ID_PATTERN.matcher(modId).matches() : "Errors list " + errorList + " didn't match the mod ID pattern!";
		return errorList.isEmpty();
	}

	private static ModResolutionException describeError(Map<ModLoadOption, MandatoryModIdDefinition> roots, List<ModLink> causes) {
		// TODO: Create a graph from roots to each other and then build the error through that!
		// implementor's note: IDK how to graph, have this mess instead
		StringBuilder errors = new StringBuilder("Error");
		if (causes.size() > 1) {
			errors.append('s');
		}
		errors.append(" involving mod");
		if (roots.size() > 1) {
			errors.append('s');
		}
		errors.append(' ').append(roots.keySet().stream()
				.map(ModResolver::getLoadOptionDescription)
				.collect(Collectors.joining(", ")))
				.append(':');
		for (ModLink cause : causes) {
			errors.append('\n');
			if (cause instanceof ModDep) {
				ModDep dep = (ModDep) cause;
				errors.append("x Mod ").append(getLoadOptionDescription(dep.source))
						.append(" requires ").append(getDependencyVersionRequirements(dep.publicDep))
						.append(" of ");
				ModIdDefinition def = dep.on;
				MandatoryModIdDefinition manDef = null;
				if (def instanceof MandatoryModIdDefinition) {
					manDef = (MandatoryModIdDefinition) def;
				} else if (def instanceof OverridenModIdDefintion) {
					manDef = ((OverridenModIdDefintion) def).overrider;
				}
				if (manDef == null) {
					errors.append("unknown mod '").append(def.getModId()).append("'\n")
							.append("\t+ You must install ").append(getDependencyVersionRequirements(dep.publicDep))
							.append(" of '").append(def.getModId()).append("'.");
				} else {
					errors.append("mod ").append(getCandidateName(manDef.candidate))
							.append("\n\t+ You must install ").append(getDependencyVersionRequirements(dep.publicDep))
							.append(" of ").append(getCandidateName(manDef.candidate)).append('.')
							.append("\n\t+ Your current version of ").append(getCandidateName(manDef.candidate))
							.append(" is ").append(getCandidateFriendlyVersion(manDef.candidate)).append(".");
				}
			} else if (cause instanceof ModConflict) {
				ModConflict conflict = (ModConflict) cause;
				errors.append("x Mod ").append(getLoadOptionDescription(conflict.source))
						.append(" conflicts with ").append(getDependencyVersionRequirements(conflict.publicDep))
						.append(" of mod ").append(getLoadOptionDescription(conflict.with))
						.append("\n\t+ The developer(s) of ").append(getCandidateName(conflict.source))
						.append(" have found that version ").append(getCandidateFriendlyVersion(conflict.with))
						.append(" of ").append(getCandidateName(conflict.with))
						.append(" critically conflicts with their mod.")
						.append("\n\t+ You must remove one of the mods.");
			} else {
				errors.append("x Unknown error type?")
						.append("\n\t+ cause.getClass() =>")
						.append("\n\t\t").append(cause.getClass().getName())
						.append("\n\t+ cause.toString() =>")
						.append("\n\t\t").append(cause.toString());
			}
		}
		for (ModLoadOption involvedMod : roots.keySet()) {
			// TODO: See if I can get a result similar to appendJiJInfo (which requires a complete "mod ID -> candidate" map)
			errors.append("\n+ Mod ").append(getLoadOptionDescription(involvedMod)).append(" is being loaded from \"")
					.append(involvedMod.getLoadSource()).append("\".");
		}
		return new ModResolutionException(errors.toString());
	}

	private static String getLoadOptionDescription(ModLoadOption loadOption) {
		return getCandidateName(loadOption) + " v" + getCandidateFriendlyVersion(loadOption);
	}

	private static String getCandidateName(ModLoadOption candidate) {
		return getCandidateName(candidate.candidate);
	}

	private static String getCandidateFriendlyVersion(ModLoadOption candidate) {
		return getCandidateFriendlyVersion(candidate.candidate);
	}

	static class UrlProcessAction extends RecursiveAction {
		private final FabricLoader loader;
		private final Map<String, ModCandidateSet> candidatesById;
		private final URL url;
		private final int depth;
		private final boolean requiresRemap;

		UrlProcessAction(FabricLoader loader, Map<String, ModCandidateSet> candidatesById, URL url, int depth, boolean requiresRemap) {
			this.loader = loader;
			this.candidatesById = candidatesById;
			this.url = url;
			this.depth = depth;
			this.requiresRemap = requiresRemap;
		}

		@Override
		protected void compute() {
			FileSystemUtil.FileSystemDelegate jarFs;
			Path path, modJson, rootDir;
			URL normalizedUrl;

			loader.getLogger().debug("Testing " + url);

			try {
				path = UrlUtil.asPath(url).normalize();
				// normalize URL (used as key for nested JAR lookup)
				normalizedUrl = UrlUtil.asUrl(path);
			} catch (UrlConversionException e) {
				throw new RuntimeException("Failed to convert URL " + url + "!", e);
			}

			if (Files.isDirectory(path)) {
				// Directory
				modJson = path.resolve("fabric.mod.json");
				rootDir = path;

				if (loader.isDevelopmentEnvironment() && !Files.exists(modJson)) {
					loader.getLogger().warn("Adding directory " + path + " to mod classpath in development environment - workaround for Gradle splitting mods into two directories");
					synchronized (launcherSyncObject) {
						FabricLauncherBase.getLauncher().propose(url);
					}
				}
			} else {
				// JAR file
				try {
					jarFs = FileSystemUtil.getJarFileSystem(path, false);
					modJson = jarFs.get().getPath("fabric.mod.json");
					rootDir = jarFs.get().getRootDirectories().iterator().next();
				} catch (IOException e) {
					throw new RuntimeException("Failed to open mod JAR at " + path + "!");
				} catch (ZipError e) {
					throw new RuntimeException("Jar at " + path + " is corrupted, please redownload it!");
				}
			}

			LoaderModMetadata[] info;

			try {
				info = new LoaderModMetadata[] { ModMetadataParser.parseMetadata(loader.getLogger(), modJson) };
			} catch (ParseMetadataException.MissingRequired e){
				throw new RuntimeException(String.format("Mod at \"%s\" has an invalid fabric.mod.json file! The mod is missing the following required field!", path), e);
			} catch (MalformedJsonException | ParseMetadataException e) {
				throw new RuntimeException(String.format("Mod at \"%s\" has an invalid fabric.mod.json file!", path), e);
			} catch (NoSuchFileException e) {
				loader.getLogger().warn(String.format("Non-Fabric mod JAR at \"%s\", ignoring", path));
				info = new LoaderModMetadata[0];
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to open fabric.mod.json for mod at \"%s\"!", path), e);
			} catch (Throwable t) {
				throw new RuntimeException(String.format("Failed to parse mod metadata for mod at \"%s\"", path), t);
			}

			for (LoaderModMetadata i : info) {
				ModCandidate candidate = new ModCandidate(i, normalizedUrl, depth, requiresRemap);
				boolean added;

				if (candidate.getInfo().getId() == null || candidate.getInfo().getId().isEmpty()) {
					throw new RuntimeException(String.format("Mod file `%s` has no id", candidate.getOriginUrl().getFile()));
				}

				if (!MOD_ID_PATTERN.matcher(candidate.getInfo().getId()).matches()) {
					List<String> errorList = new ArrayList<>();
					isModIdValid(candidate.getInfo().getId(), errorList);
					StringBuilder fullError = new StringBuilder("Mod id `");
					fullError.append(candidate.getInfo().getId()).append("` does not match the requirements because");

					if (errorList.size() == 1) {
						fullError.append(" it ").append(errorList.get(0));
					} else {
						fullError.append(":");
						for (String error : errorList) {
							fullError.append("\n  - It ").append(error);
						}
					}

					throw new RuntimeException(fullError.toString());
				}

				added = candidatesById.computeIfAbsent(candidate.getInfo().getId(), ModCandidateSet::new).add(candidate);

				if (!added) {
					loader.getLogger().debug(candidate.getOriginUrl() + " already present as " + candidate);
				} else {
					loader.getLogger().debug("Adding " + candidate.getOriginUrl() + " as " + candidate);

					List<Path> jarInJars = inMemoryCache.computeIfAbsent(candidate.getOriginUrl().toString(), (u) -> {
						loader.getLogger().debug("Searching for nested JARs in " + candidate);
						loader.getLogger().debug(u);
						Collection<NestedJarEntry> jars = candidate.getInfo().getJars();
						List<Path> list = new ArrayList<>(jars.size());

						jars.stream()
							.map((j) -> rootDir.resolve(j.getFile().replace("/", rootDir.getFileSystem().getSeparator())))
							.forEach((modPath) -> {
								if (!Files.isDirectory(modPath) && modPath.toString().endsWith(".jar")) {
									// TODO: pre-check the JAR before loading it, if possible
									loader.getLogger().debug("Found nested JAR: " + modPath);
									Path dest = inMemoryFs.getPath(UUID.randomUUID() + ".jar");

									try {
										Files.copy(modPath, dest);
									} catch (IOException e) {
										throw new RuntimeException("Failed to load nested JAR " + modPath + " into memory (" + dest + ")!", e);
									}

									list.add(dest);

									try {
										readableNestedJarPaths.put(UrlUtil.asUrl(dest).toString(), String.format("%s!%s", getReadablePath(loader, candidate), modPath));
									} catch (UrlConversionException e) {
										e.printStackTrace();
									}
								}
							});

						return list;
					});

					if (!jarInJars.isEmpty()) {
						invokeAll(
							jarInJars.stream()
								.map((p) -> {
									try {
										return new UrlProcessAction(loader, candidatesById, UrlUtil.asUrl(p.normalize()), depth + 1, requiresRemap);
									} catch (UrlConversionException e) {
										throw new RuntimeException("Failed to turn path '" + p.normalize() + "' into URL!", e);
									}
								}).collect(Collectors.toList())
						);
					}
				}
			}

			/* if (jarFs != null) {
				jarFs.close();
			} */
		}
	}

	public Map<String, ModCandidate> resolve(FabricLoader loader) throws ModResolutionException {
		ConcurrentMap<String, ModCandidateSet> candidatesById = new ConcurrentHashMap<>();

		long time1 = System.currentTimeMillis();
		Queue<UrlProcessAction> allActions = new ConcurrentLinkedQueue<>();
		ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
		for (ModCandidateFinder f : candidateFinders) {
			f.findCandidates(loader, (u, requiresRemap) -> {
				UrlProcessAction action = new UrlProcessAction(loader, candidatesById, u, 0, requiresRemap);
				allActions.add(action);
				pool.execute(action);
			});
		}

		// add builtin mods
		for (BuiltinMod mod : loader.getGameProvider().getBuiltinMods()) {
			candidatesById.computeIfAbsent(mod.metadata.getId(), ModCandidateSet::new).add(new ModCandidate(new BuiltinMetadataWrapper(mod.metadata), mod.url, 0, false));
		}

		boolean tookTooLong = false;
		Throwable exception = null;
		try {
			pool.shutdown();
			// Comment out for debugging
			pool.awaitTermination(30, TimeUnit.SECONDS);
			for (UrlProcessAction action : allActions) {
				if (!action.isDone()) {
					tookTooLong = true;
				} else {
					Throwable t = action.getException();
					if (t != null) {
						if (exception == null) {
							exception = t;
						} else {
							exception.addSuppressed(t);
						}
					}
				}
			}
		} catch (InterruptedException e) {
			throw new ModResolutionException("Mod resolution took too long!", e);
		}
		if (tookTooLong) {
			throw new ModResolutionException("Mod resolution took too long!");
		}
		if (exception != null) {
			throw new ModResolutionException("Mod resolution failed!", exception);
		}

		long time2 = System.currentTimeMillis();
		Map<String, ModCandidate> result = findCompatibleSet(loader.getLogger(), candidatesById);

		long time3 = System.currentTimeMillis();
		loader.getLogger().debug("Mod resolution detection time: " + (time2 - time1) + "ms");
		loader.getLogger().debug("Mod resolution time: " + (time3 - time2) + "ms");

		for (ModCandidate candidate : result.values()) {
			if (candidate.getInfo().getSchemaVersion() < ModMetadataParser.LATEST_VERSION) {
				loader.getLogger().warn("Mod ID " + candidate.getInfo().getId() + " uses outdated schema version: " + candidate.getInfo().getSchemaVersion() + " < " + ModMetadataParser.LATEST_VERSION);
			}

			candidate.getInfo().emitFormatWarnings(loader.getLogger());
		}

		return result;
	}

	public static FileSystem getInMemoryFs() {
		return inMemoryFs;
	}

	// Classes used for dependency comparison
	// All of these are package-private as they use fairly generic names for mods
	// (Plus these are classes rather than hard-coded to make expanding easier)

	/** Base definition of something that can either be completely loaded or not loaded. (Usually this is just a mod jar
	 * file, but in the future this might refer to something else that loader has control over). */
	static abstract class LoadOption {}

	static class ModLoadOption extends LoadOption {
		final ModCandidate candidate;

		/** Used to identify this {@link ModLoadOption} against others with the same modid. A value of -1 indicates that
		 * this is the only {@link LoadOption} for the given modid. */
		final int index;

		ModLoadOption(ModCandidate candidate, int index) {
			this.candidate = candidate;
			this.index = index;
		}

		String modId() {
			return candidate.getInfo().getId();
		}

		@Override
		public String toString() {
			return shortString();
		}
		
		String shortString() {
			if (index == -1) {
				return "mod '" + modId() + "'";
			} else {
				return "mod '" + modId() + "'#" + (index + 1);
			}
		}

		String fullString() {
			return shortString() + " " + getSpecificInfo();
		}

		String getLoadSource() {
			return getReadablePath(FabricLoader.INSTANCE, candidate);
		}

		String getSpecificInfo() {
			LoaderModMetadata info = candidate.getInfo();
			return "version " + info.getVersion() + " loaded from " + getLoadSource();
		}
	}

	/** Used for the "inverse load" condition - if this is required by a {@link ModLink} then it means the
	 * {@link LoadOption} must not be loaded. */
	static final class NegatedLoadOption extends LoadOption {
		final LoadOption not;

		public NegatedLoadOption(LoadOption not) {
			this.not = not;
		}

		@Override
		public String toString() {
			return "NOT " + not;
		}
	}

	static final class LoadOptionNegator implements INegator {
		@Override
		public boolean isNegated(Object thing) {
			return thing instanceof NegatedLoadOption;
		}

		@Override
		public Object unNegate(Object thing) {
			return ((NegatedLoadOption) thing).not;
		}
	}

	/** Base definition of a link between one or more {@link LoadOption}s, that */
	static abstract class ModLink implements Comparable<ModLink> {
		static final List<Class<? extends ModLink>> LINK_ORDER = new ArrayList<>();

		static {
			LINK_ORDER.add(MandatoryModIdDefinition.class);
			LINK_ORDER.add(OptionalModIdDefintion.class);
			LINK_ORDER.add(ModDep.class);
			LINK_ORDER.add(ModConflict.class);
		}

		abstract ModLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException;

		/** @return A description of the link. */
		@Override
		public abstract String toString();

		@Override
		public final int compareTo(ModLink o) {
			if (o.getClass() == getClass()) {
				return compareToSelf(o);
			} else {
				int i0 = LINK_ORDER.indexOf(getClass());
				int i1 = LINK_ORDER.indexOf(o.getClass());
				return Integer.compare(i1, i0);
			}
		}

		protected abstract int compareToSelf(ModLink o);
	}

	/** A concrete definition of a modid. This also maps the modid to the {@link LoadOption} candidates, and so is used
	 * instead of {@link LoadOption} in other links. */
	static abstract class ModIdDefinition extends ModLink {
		abstract String getModId();

		/** @return An array of all the possible {@link LoadOption} instances that can define this modid. May be empty,
		 *         but will never be null. */
		abstract ModLoadOption[] sources();

		@Override
		protected int compareToSelf(ModLink o) {
			ModIdDefinition other = (ModIdDefinition) o;
			return getModId().compareTo(other.getModId());
		}
	}

	/** A concrete definition that mandates that the modid must be loaded by the given singular {@link ModCandidate},
	 * and no others. (The resolver pre-validates that we don't have duplicate mandatory mods, so this is always valid
	 * by the time this is used). */
	static final class MandatoryModIdDefinition extends ModIdDefinition {
		final ModLoadOption candidate;

		public MandatoryModIdDefinition(ModLoadOption candidate) {
			this.candidate = candidate;
		}

		@Override
		String getModId() {
			return candidate.modId();
		}

		@Override
		ModLoadOption[] sources() {
			return new ModLoadOption[] { candidate };
		}

		@Override
		MandatoryModIdDefinition put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
			helper.clause(this, candidate);
			return this;
		}

		@Override
		public String toString() {
			return "mandatory " + candidate.fullString();
		}
	}

	/** A concrete definition that allows the modid to be loaded from any of a set of {@link ModCandidate}s. */
	static final class OptionalModIdDefintion extends ModIdDefinition {
		final String modid;
		final ModLoadOption[] sources;

		public OptionalModIdDefintion(String modid, ModLoadOption[] sources) {
			this.modid = modid;
			this.sources = sources;
		}

		@Override
		String getModId() {
			return modid;
		}

		@Override
		ModLoadOption[] sources() {
			return sources;
		}

		@Override
		OptionalModIdDefintion put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
			helper.atMost(this, 1, sources);
			return this;
		}

		@Override
		public String toString() {
			switch (sources.length) {
				case 0: return "unknown mod '" + modid + "'";
				case 1: return "optional mod '" + modid + "' (1 source)";
				default: return "optional mod '" + modid + "' (" + sources.length + " sources)";
			}
		}
	}

	/** A variant of {@link OptionalModIdDefintion} but which is overriden by a {@link MandatoryModIdDefinition} (and so
	 * none of these candidates can load). */
	static final class OverridenModIdDefintion extends ModIdDefinition {
		final MandatoryModIdDefinition overrider;
		final ModLoadOption[] sources;

		public OverridenModIdDefintion(MandatoryModIdDefinition overrider, ModLoadOption[] sources) {
			this.overrider = overrider;
			this.sources = sources;
		}

		@Override
		String getModId() {
			return overrider.getModId();
		}

		@Override
		ModLoadOption[] sources() {
			return sources;
		}

		@Override
		OverridenModIdDefintion put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
			helper.atMost(this, 0, sources);
			return this;
		}

		@Override
		public String toString() {
			return "overriden mods '" + overrider.getModId() + "' of " + sources.length + " by " + overrider;
		}
	}

	static final class ModDep extends ModLink {
		final ModLoadOption source;
		final ModDependency publicDep;
		final ModIdDefinition on;
		final List<ModLoadOption> validOptions;
		final List<ModLoadOption> invalidOptions;

		public ModDep(ModLoadOption source, ModDependency publicDep, ModIdDefinition on) {
			this.source = source;
			this.publicDep = publicDep;
			this.on = on;
			validOptions = new ArrayList<>();
			invalidOptions = new ArrayList<>();

			for (ModLoadOption option : on.sources()) {
				if (publicDep.matches(option.candidate.getInfo().getVersion())) {
					validOptions.add(option);
				} else {
					invalidOptions.add(option);
				}
			}
		}

		@Override
		ModDep put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
			List<LoadOption> clause = new ArrayList<>();
			clause.addAll(validOptions);
			clause.add(new NegatedLoadOption(source));
			helper.clause(this, clause.toArray(new LoadOption[0]));
			return this;
		}

		@Override
		public String toString() {
			return source + " depends on " + on + " version " + publicDep;
		}

		@Override
		protected int compareToSelf(ModLink o) {
			ModDep other = (ModDep) o;
			int c = source.modId().compareTo(other.source.modId());
			if (c != 0) {
				return c;
			}
			return on.compareTo(other.on);
		}
	}

	static final class ModConflict extends ModLink {
		final ModLoadOption source;
		final ModDependency publicDep;
		final ModLoadOption with;

		public ModConflict(ModLoadOption source, ModDependency publicDep, ModLoadOption with) {
			this.source = source;
			this.publicDep = publicDep;
			this.with = with;
		}

		@Override
		ModConflict put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
			helper.clause(this, new NegatedLoadOption(source), new NegatedLoadOption(with));
			return this;
		}

		@Override
		public String toString() {
			return source + " conflicts with " + with + " version " + publicDep;
		}

		@Override
		protected int compareToSelf(ModLink o) {
			ModConflict other = (ModConflict) o;
			int c = source.modId().compareTo(other.source.modId());
			if (c != 0) {
				return c;
			}
			return with.modId().compareTo(other.with.modId());
		}
	}
}
