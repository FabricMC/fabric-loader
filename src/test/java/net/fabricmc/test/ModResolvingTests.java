package net.fabricmc.test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.discovery.ModCandidate;
import net.fabricmc.loader.discovery.ModCandidateSet;
import net.fabricmc.loader.discovery.ModResolver;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.metadata.ModMetadataParser;
import net.fabricmc.loader.metadata.NestedJarEntry;

final class ModResolvingTests {
	private static final Logger LOGGER = LogManager.getLogger();

	private static Path testLocation;

	@BeforeAll
	private static void setupPaths() {
		testLocation = new File(System.getProperty("user.dir")).toPath()//
			.resolve("src")//
			.resolve("test")//
			.resolve("resources")//
			.resolve("testing")//
			.resolve("resolving");
	}

	@Test
	public void single() throws Exception {
		Map<String, ModCandidate> modSet = resolveModSet("valid", "single");

		assertModPresent(modSet, "mod-resolving-tests-single", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void duel() throws Exception {
		Map<String, ModCandidate> modSet = resolveModSet("valid", "duel");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void depends() throws Exception {
		Map<String, ModCandidate> modSet = resolveModSet("valid", "depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void includedDep() throws Exception {
		Map<String, ModCandidate> modSet = resolveModSet("valid", "included_dep");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void altDep() throws Exception {
		Map<String, ModCandidate> modSet = resolveModSet("valid", "alt_deps");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "2.0.0");
		assertNoMoreMods(modSet);
	}

	private static Map<String, ModCandidate> resolveModSet(String type, String subpath) throws Exception {

		ModResolver resolver = new ModResolver();
		Map<String, ModCandidateSet> candidateMap = new HashMap<>();

		Path modRoot = testLocation.resolve(type).resolve(subpath);

		List<Path> subFolders = Files.list(modRoot)//
			.filter(p -> p.getFileName().toString().endsWith(".jar") && Files.isDirectory(p))//
			.collect(Collectors.toCollection(ArrayList::new));

		List<Path> loadFrom = new ArrayList<>();
		int depth = 0;

		loadFrom.addAll(subFolders);

		while (!loadFrom.isEmpty()) {
			subFolders.clear();

			for (Path modPath : loadFrom) {

				URL url = modPath.toUri().toURL();
				LoaderModMetadata[] metas = { ModMetadataParser.parseMetadata(LOGGER, modPath.resolve("fabric.mod.json")) };

				for (LoaderModMetadata meta : metas) {
					ModCandidate candidate = new ModCandidate(meta, url, depth, false);
					candidateMap.computeIfAbsent(candidate.getInfo().getId(), ModCandidateSet::new).add(candidate);

					for (NestedJarEntry jar : meta.getJars()) {
						Path sub = modPath;

						for (String part : jar.getFile().split("/")) {
							sub = sub.resolve(part);
						}

						subFolders.add(sub);
					}
				}
			}

			loadFrom.clear();
			loadFrom.addAll(subFolders);
			depth++;
		}

		return resolver.findCompatibleSet(LOGGER, candidateMap);
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertModPresent(Map<String, ModCandidate> map, String modid, String version) {
		ModCandidate mod = map.remove(modid);

		if (mod == null) {
			Assertions.fail(modid + " is missing from " + map);
		} else {
			Assertions.assertEquals(version, mod.getInfo().getVersion().getFriendlyString());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertModMissing(Map<String, ModCandidate> map, String modid) {
		ModCandidate mod = map.remove(modid);

		if (mod != null) {
			Assertions.fail(modid + " is not missing, and instead is loaded: " + mod);
		}
	}

	private static void assertNoMoreMods(Map<String, ModCandidate> modSet) {
		if (!modSet.isEmpty()) {
			Assertions.fail("Expected to find no more mods loaded, but found: " + modSet);
		}
	}
}
