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

import com.google.common.base.Joiner;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;
import com.google.gson.*;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.metadata.ModMetadataV0;
import net.fabricmc.loader.util.FileSystemUtil;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.sat4j.core.VecInt;
import net.fabricmc.loader.util.sat4j.minisat.SolverFactory;
import net.fabricmc.loader.util.sat4j.specs.ContradictionException;
import net.fabricmc.loader.util.sat4j.specs.IProblem;
import net.fabricmc.loader.util.sat4j.specs.ISolver;
import net.fabricmc.loader.util.sat4j.specs.IVecInt;
import net.fabricmc.loader.util.sat4j.specs.TimeoutException;
import net.fabricmc.loader.util.version.VersionDeserializer;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
	private static final Map<URL, List<Path>> inMemoryCache = new HashMap<>();

	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(Version.class, new VersionDeserializer())
		.registerTypeAdapter(ModMetadataV0.Side.class, new ModMetadataV0.Side.Deserializer())
		.registerTypeAdapter(ModMetadataV0.Mixins.class, new ModMetadataV0.Mixins.Deserializer())
		.registerTypeAdapter(ModMetadataV0.Links.class, new ModMetadataV0.Links.Deserializer())
		.registerTypeAdapter(ModMetadataV0.Dependency.class, new ModMetadataV0.Dependency.Deserializer())
		.registerTypeAdapter(ModMetadataV0.Person.class, new ModMetadataV0.Person.Deserializer())
		.create();
	private static final JsonParser JSON_PARSER = new JsonParser();
	private static final Pattern MOD_ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{1,63}");

	private final List<ModCandidateFinder> candidateFinders = new ArrayList<>();

	public ModResolver() {
	}

	public void addCandidateFinder(ModCandidateFinder f) {
		candidateFinders.add(f);
	}

	private static Set<ModCandidate> createNewestVersionSet() {
		return new TreeSet<>((a, b) -> {
			Version av = a.getInfo().getVersion();
			Version bv = b.getInfo().getVersion();

			if (av instanceof SemanticVersion && bv instanceof SemanticVersion) {
				return ((SemanticVersion) bv).compareTo((SemanticVersion) av);
			} else {
				return 0;
			}
		});
	}

	protected static LoaderModMetadata[] getMods(InputStream in) {
		JsonElement el = JSON_PARSER.parse(new InputStreamReader(in));
		if (el.isJsonObject()) {
			return new LoaderModMetadata[] { GSON.fromJson(el, ModMetadataV0.class) };
		} else if (el.isJsonArray()) {
			JsonArray array = el.getAsJsonArray();
			LoaderModMetadata[] mods = new LoaderModMetadata[array.size()];
			for (int i = 0; i < array.size(); i++) {
				mods[i] = GSON.fromJson(array.get(i), ModMetadataV0.class);
			}
			return mods;
		}

		return new LoaderModMetadata[0];
	}

	private static IVecInt toVecInt(IntStream stream) {
		return new VecInt(stream.toArray());
	}

	// TODO: Find a way to sort versions of mods by suggestions and conflicts (not crucial, though)
	public Map<String, ModCandidate> findCompatibleSet(Logger logger, Map<String, Set<ModCandidate>> modCandidateMap) throws ModResolutionException {
		// Inspired by http://0install.net/solver.html
		// probably also horrendously slow, for now

		// Step 1: Create a Set of all mandatory mods. For now, this is all mods present.
		Set<String> mandatoryMods = modCandidateMap.keySet();

		// Step 2: Map all the ModCandidates to DIMACS-format positive integers.
		int varCount = 1;
		Map<ModCandidate, Integer> candidateIntMap = new HashMap<>();
		List<ModCandidate> intCandidateMap = new ArrayList<>(modCandidateMap.size() * 2);
		intCandidateMap.add(null);
		for (Set<ModCandidate> m : modCandidateMap.values()) {
			for (ModCandidate candidate : m) {
				candidateIntMap.put(candidate, varCount++);
				intCandidateMap.add(candidate);
			}
		}

		// Step 3:
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
					throw new ModResolutionException("Could not resolve valid mod collection (at: adding mod " + id + ")", e);
				}
			}

			for (ModCandidate mod : candidateIntMap.keySet()) {
				int modClauseId = candidateIntMap.get(mod);

				// Each mod's requirements must be satisfied, if it is to be present.
				// mod => ((a or b) AND (d or e))
				// \> not mod OR ((a or b) AND (d or e))
				// \> ((not mod OR a OR b) AND (not mod OR d OR e))

				for (ModDependency dep : mod.getInfo().getDepends()) {
					if (!modCandidateMap.containsKey(dep.getModId())) {
						// bail early
						throw new RuntimeException("Mod " + mod.getInfo().getId() + " depends on mod " + dep.getModId() + ", which is missing!");
					}

					int[] matchingCandidates = modCandidateMap.get(dep.getModId())
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
						throw new ModResolutionException("Could not resolve valid mod collection (at: " + mod.getInfo().getId() + " requires " + dep.getModId() + ")", e);
					}
				}

				// Each mod's breaks must be NOT satisfied, if it is to be present.
				// mod => (not a AND not b AND not d AND not e))
				// \> not mod OR (not a AND not b AND not d AND not e)
				// \> (not mod OR not a) AND (not mod OR not b) ...

				for (ModDependency dep : mod.getInfo().getBreaks()) {
					int[] matchingCandidates = modCandidateMap.get(dep.getModId())
						.stream()
						.filter((c) -> dep.matches(c.getInfo().getVersion()))
						.mapToInt(candidateIntMap::get)
						.toArray();

					try {
						for (int m : matchingCandidates) {
							solver.addClause(new VecInt(new int[] { -modClauseId, -m }));
						}
					} catch (ContradictionException e) {
						throw new ModResolutionException("Could not resolve valid mod collection (at: " + mod.getInfo().getId() + " breaks " + dep.getModId() + ")", e);
					}
				}
			}

			//noinspection UnnecessaryLocalVariable
			IProblem problem = solver;
			IVecInt assumptions = new VecInt(modCandidateMap.size());

			for (String mod : modCandidateMap.keySet()) {
				int pos = assumptions.size();
				assumptions = assumptions.push(0);
				Set<ModCandidate> candidates = createNewestVersionSet();
				candidates.addAll(modCandidateMap.get(mod));
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
						throw new ModResolutionException("Could not resolve mod collection including mandatory mod '" + mod + "'");
					} else {
						assumptions = assumptions.pop();
					}
				}
			}

			// assume satisfied
			int[] model = problem.model();
			Map<String, ModCandidate> result = new HashMap<>();
			for (int i : model) {
				if (i <= 0) {
					continue;
				}

				ModCandidate candidate = intCandidateMap.get(i);
				if (result.containsKey(candidate.getInfo().getId())) {
					throw new ModResolutionException("Duplicate ID '" + candidate.getInfo().getId() + "' after solving - wrong constraints?");
				} else {
					result.put(candidate.getInfo().getId(), candidate);
				}
			}

			Set<String> missingMods = new HashSet<>();
			for (String m : mandatoryMods) {
				if (!result.keySet().contains(m)) {
					missingMods.add(m);
				}
			}

			if (!missingMods.isEmpty()) {
				throw new ModResolutionException("Missing mods! Wrong constraints? " + Joiner.on(", ").join(missingMods));
			}

			return result;
		} catch (TimeoutException e) {
			throw new ModResolutionException("Mod collection took too long to be resolved", e);
		}
	}

	static class UrlProcessAction extends RecursiveAction {
		private final FabricLoader loader;
		private final Map<String, Set<ModCandidate>> candidatesById;
		private final URL url;

		public UrlProcessAction(FabricLoader loader, Map<String, Set<ModCandidate>> candidatesById, URL url) {
			this.loader = loader;
			this.candidatesById = candidatesById;
			this.url = url;
		}

		@Override
		protected void compute() {
			try {
				FileSystemUtil.FileSystemDelegate jarFs = null;
				Path path = UrlUtil.asPath(url).normalize();
				Path modJson;
				Path jarsDir;

				loader.getLogger().debug("Testing " + url);

				// normalize URL (used as key for nested JAR lookup)
				URL normalizedUrl = UrlUtil.asUrl(path);

				if (Files.isDirectory(path)) {
					// Directory
					modJson = path.resolve("fabric.mod.json");
					jarsDir = path.resolve("jars");
				} else {
					// JAR file
					jarFs = FileSystemUtil.getJarFileSystem(path, false);
					modJson = jarFs.get().getPath("fabric.mod.json");
					jarsDir = jarFs.get().getPath("jars");
				}

				LoaderModMetadata[] info;

				try (InputStream stream = Files.newInputStream(modJson)) {
					info = getMods(stream);
				} catch (NoSuchFileException e) {
					info = new LoaderModMetadata[0];
				}

				for (LoaderModMetadata i : info) {
					ModCandidate candidate = new ModCandidate(i, normalizedUrl);
					boolean added;

					if (candidate.getInfo().getId() == null || candidate.getInfo().getId().isEmpty()) {
						throw new RuntimeException(String.format("Mod file `%s` has no id", candidate.getOriginUrl().getFile()));
					}

					if (!MOD_ID_PATTERN.matcher(candidate.getInfo().getId()).matches()) {
						throw new RuntimeException(String.format("Mod id `%s` does not match the requirements", candidate.getInfo().getId()));
					}

					synchronized (candidatesById) {
						Set<ModCandidate> candidateSet = candidatesById.computeIfAbsent(candidate.getInfo().getId(), (s) -> new HashSet<>() /* version sorting not necessary at this stage */);
						added = candidateSet.add(candidate);
					}

					if (!added) {
						loader.getLogger().debug(candidate.getOriginUrl() + " already present as " + candidate);
					} else {
						loader.getLogger().debug("Adding " + candidate.getOriginUrl() + " as " + candidate);

						if (Files.isDirectory(jarsDir)) {
							List<Path> jarInJars = inMemoryCache.computeIfAbsent(candidate.getOriginUrl(), (u) -> {
								loader.getLogger().debug("Searching for nested JARs in " + candidate);
								List<Path> list = new ArrayList<>();

								try {
									Files.walk(jarsDir, 1).forEach((modPath) -> {
										if (!Files.isDirectory(modPath) && modPath.toString().endsWith(".jar")) {
											// TODO: pre-check the JAR before loading it, if possible
											// TODO
											loader.getLogger().debug("Found nested JAR: " + modPath);
											Path dest = inMemoryFs.getPath(UUID.randomUUID() + ".jar");

											try {
												Files.copy(modPath, dest);
											} catch (IOException e) {
												throw new RuntimeException(e);
											}

											list.add(dest);
										}
									});
								} catch (IOException e) {
									throw new RuntimeException(e);
								}

								return list;
							});

							invokeAll(
								jarInJars.stream()
									.map((p) -> {
										try {
											return new UrlProcessAction(loader, candidatesById, UrlUtil.asUrl(p.normalize()));
										} catch (UrlConversionException e) {
											throw new RuntimeException(e);
										}
									}).collect(Collectors.toList())
							);
						}
					}
				}

				/* if (jarFs != null) {
					jarFs.close();
				} */
			} catch (UrlConversionException | IOException e) {
				throw new RuntimeException(url.toString(), e);
			}
		}
	}

	public Map<String, ModCandidate> resolve(FabricLoader loader) throws ModResolutionException {
		Map<String, Set<ModCandidate>> candidatesById = new HashMap<>();

		long time1 = System.currentTimeMillis();

		ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
		for (ModCandidateFinder f : candidateFinders) {
			f.findCandidates(loader, (u) -> pool.execute(new UrlProcessAction(loader, candidatesById, u)));
		}

		try {
			pool.shutdown();
			pool.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Mod resolution took too long!", e);
		}

		long time2 = System.currentTimeMillis();
		Map<String, ModCandidate> result = findCompatibleSet(loader.getLogger(), candidatesById);

		long time3 = System.currentTimeMillis();
		loader.getLogger().debug("Mod resolution detection time: " + (time2 - time1) + "ms");
		loader.getLogger().debug("Mod resolution time: " + (time3 - time2) + "ms");

		return result;
	}
}
