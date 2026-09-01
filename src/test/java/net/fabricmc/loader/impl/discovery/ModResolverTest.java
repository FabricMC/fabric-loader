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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.MockV1ModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class ModResolverTest {
	@Test
	public void testIncompatibleJiJNotLoaded() throws ModResolutionException, VersionParsingException {
		ModDependency depOnC = new ModDependencyImpl(ModDependency.Kind.DEPENDS, "c", Arrays.asList("*"));
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "1.0.0").addDependency(depOnC)));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "a", Arrays.asList("*"))));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertFalse(solution.isModLoaded("aa"));
	}

	@Test
	public void testIncompatibleJiJFailure() throws VersionParsingException {
		ModDependency depOnC = new ModDependencyImpl(ModDependency.Kind.DEPENDS, "c", Arrays.asList("*"));
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "1.0.0").addDependency(depOnC))
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "aa", Arrays.asList("*"))));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0"));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);

		Assertions.assertThrows(ModResolutionException.class, () -> solveMods(modCandidates));
	}

	@Test
	public void testMultiVersionJiJ() throws ModResolutionException, VersionParsingException {
		ModDependency depOnC = new ModDependencyImpl(ModDependency.Kind.DEPENDS, "c", Arrays.asList("*"));
		ModDependency breakOnC = new ModDependencyImpl(ModDependency.Kind.BREAKS, "c", Arrays.asList("*"));
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "1.0.0").addDependency(depOnC))
				.addNestedMod(MockV1ModMetadata.builder("aa", "2.0.0").addDependency(breakOnC))
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "aa", Arrays.asList("*"))));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "a", Arrays.asList("*"))));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("aa"));
	}

	@Test
	public void testDuplicateRootMods() {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0"));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0"));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);

		Assertions.assertThrows(ModResolutionException.class, () -> solveMods(modCandidates));
	}

	@Test
	public void testRootOverrides() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "2.0.0"))
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "aa", Arrays.asList("*"))));
		ModCandidateImpl aaMod = createMod(MockV1ModMetadata.builder("aa", "1.0.1"));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, aaMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("aa"));
		Assertions.assertEquals(Version.parse("1.0.1"), solution.getVersion("aa"));
	}

	@Test
	public void testJiJOverrides() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "2.0.0"))
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "aa", Arrays.asList(">=2"))));
		ModCandidateImpl aaMod = createMod(MockV1ModMetadata.builder("aa", "1.0.0"));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, aaMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("aa"));
		Assertions.assertEquals(Version.parse("2.0.0"), solution.getVersion("aa"));
	}

	@Test
	public void testDuplicateRootModsOfDiffVers() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0"));
		ModCandidateImpl aMod2 = createMod(MockV1ModMetadata.builder("a", "2.0.0"));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "a", Arrays.asList("*"))));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, aMod2);
		discoverMod(modCandidates, bMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("a"));
		Assertions.assertEquals(Version.parse("2.0.0"), solution.getVersion("a"));
	}

	@Test
	public void testDuplicateRootModsOfDiffVersLowestCompat() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0"));
		ModCandidateImpl aMod2 = createMod(MockV1ModMetadata.builder("a", "2.0.0"));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.BREAKS, "a", Arrays.asList(">=2.0.0"))));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, aMod2);
		discoverMod(modCandidates, bMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("b"));
		Assertions.assertEquals(Version.parse("1.0.0"), solution.getVersion("b"));
	}

	@Test
	public void testFixSuggestionUsesAllowedVersionForBreaks() throws VersionParsingException, ContradictionException, TimeoutException, ModResolutionException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.BREAKS, "b", Arrays.asList("<0.5.3", ">0.5.3"))));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "0.5.11+mc1.20.1")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.BREAKS, "a", Arrays.asList("<=1.0.0"))));

		List<ModCandidateImpl> allModsSorted = new ArrayList<>();
		discoverMod(allModsSorted, aMod);
		discoverMod(allModsSorted, bMod);

		Map<String, List<ModCandidateImpl>> modsById = new LinkedHashMap<>();
		ModPrioSorter.sort(allModsSorted, modsById);

		ModSolver.Result result = ModSolver.solve(allModsSorted, modsById, new HashMap<>(), new ArrayList<>());
		Assertions.assertFalse(result.success);

		ModSolver.AddModVar replacement = result.fix.modReplacements.keySet().stream()
				.filter(mod -> mod.getId().equals("b"))
				.findFirst()
				.orElse(null);
		Assertions.assertNotNull(replacement);
		Assertions.assertEquals(1, replacement.getVersionIntervals().size());

		VersionInterval interval = replacement.getVersionIntervals().get(0);
		Assertions.assertEquals(Version.parse("0.5.3"), interval.getMin());
		Assertions.assertTrue(interval.isMinInclusive());
		Assertions.assertEquals(Version.parse("0.5.3"), interval.getMax());
		Assertions.assertTrue(interval.isMaxInclusive());
	}

	@Test
	public void testProvidedOverride() throws ModResolutionException, VersionParsingException {
		// Same Version
		ModCandidateImpl faker1 = createMod(MockV1ModMetadata.builder("faker", "1.0.0").addProvides("a"));
		ModCandidateImpl aMod1 = createMod(MockV1ModMetadata.builder("a", "1.0.0"));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, faker1);
		discoverMod(modCandidates, aMod1);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("a"));
		Assertions.assertEquals(Version.parse("1.0.0"), solution.getVersion("a"));
		Assertions.assertTrue(solution.getContainer("a").getOriginUrl().toString().endsWith("faker.jar"));

		// Provided greater version
		ModCandidateImpl faker2 = createMod(MockV1ModMetadata.builder("faker", "2.0.0").addProvides("a"));
		ModCandidateImpl aMod2 = createMod(MockV1ModMetadata.builder("a", "1.0.0"));

		modCandidates = new ArrayList<>();
		discoverMod(modCandidates, faker2);
		discoverMod(modCandidates, aMod2);

		solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("a"));
		Assertions.assertEquals(Version.parse("2.0.0"), solution.getVersion("a"));
		Assertions.assertTrue(solution.getContainer("a").getOriginUrl().toString().endsWith("faker.jar"));

		// Provided lesser version
		ModCandidateImpl faker3 = createMod(MockV1ModMetadata.builder("faker", "1.0.0").addProvides("a"));
		ModCandidateImpl aMod3 = createMod(MockV1ModMetadata.builder("a", "2.0.0"));

		modCandidates = new ArrayList<>();
		discoverMod(modCandidates, faker3);
		discoverMod(modCandidates, aMod3);

		solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("a"));
		Assertions.assertEquals(Version.parse("1.0.0"), solution.getVersion("a"));
		Assertions.assertTrue(solution.getContainer("a").getOriginUrl().toString().endsWith("faker.jar"));

		// Provided conflicting version
		ModCandidateImpl faker4 = createMod(MockV1ModMetadata.builder("faker", "1.0.0").addProvides("a")
				.addDependency(new ModDependencyImpl(ModDependency.Kind.BREAKS, "b", Collections.singletonList("*"))));
		ModCandidateImpl aMod4 = createMod(MockV1ModMetadata.builder("a", "2.0.0"));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "1.0.0"));

		modCandidates = new ArrayList<>();
		discoverMod(modCandidates, faker4);
		discoverMod(modCandidates, aMod4);
		discoverMod(modCandidates, bMod);

		List<ModCandidateImpl> finalModCandidates = modCandidates;
		Assertions.assertThrows(ModResolutionException.class, () -> solveMods(finalModCandidates));
	}

	@Test
	public void testOptionalJiJChangingModList() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "2.0.0")
						.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "dd", Arrays.asList("1.x")))));
		ModCandidateImpl bMod = createMod(MockV1ModMetadata.builder("b", "2.0.0"));
		ModCandidateImpl cMod = createMod(MockV1ModMetadata.builder("c", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("cc", "1.0.0")));
		ModCandidateImpl dMod = createMod(MockV1ModMetadata.builder("d", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("dd", "2.0.0"))
				.addNestedMod(MockV1ModMetadata.builder("dd", "1.0.0")));

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);
		discoverMod(modCandidates, cMod);
		discoverMod(modCandidates, dMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("dd"));
		Assertions.assertEquals(Version.parse("1.0.0"), solution.getVersion("dd"));

		// Remake mods without the dependency
		aMod = createMod(MockV1ModMetadata.builder("a", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("aa", "2.0.0")));
		bMod = createMod(MockV1ModMetadata.builder("b", "2.0.0"));
		cMod = createMod(MockV1ModMetadata.builder("c", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("cc", "1.0.0")));
		dMod = createMod(MockV1ModMetadata.builder("d", "1.0.0")
				.addNestedMod(MockV1ModMetadata.builder("dd", "2.0.0"))
				.addNestedMod(MockV1ModMetadata.builder("dd", "1.0.0")));

		modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);
		discoverMod(modCandidates, cMod);
		discoverMod(modCandidates, dMod);

		solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("dd"));
		Assertions.assertEquals(Version.parse("2.0.0"), solution.getVersion("dd"));
	}

	@Test
	public void testCircular() throws ModResolutionException, VersionParsingException {
		ModCandidateImpl aMod = createMod(
				MockV1ModMetadata.builder("a", "1.0.0")
						.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "b", Arrays.asList("*")))
		);
		ModCandidateImpl bMod = createMod(
				MockV1ModMetadata.builder("b", "1.0.0")
						.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "c", Arrays.asList("*")))
		);
		ModCandidateImpl cMod = createMod(
				MockV1ModMetadata.builder("c", "1.0.0")
						.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "a", Arrays.asList("*")))
		);

		List<ModCandidateImpl> modCandidates = new ArrayList<>();
		discoverMod(modCandidates, aMod);
		discoverMod(modCandidates, bMod);
		discoverMod(modCandidates, cMod);

		Solution solution = solveMods(modCandidates);
		Assertions.assertTrue(solution.isModLoaded("b"));
	}

	private static Solution solveMods(List<ModCandidateImpl> modCandidates) throws ModResolutionException {
		modCandidates = ModResolver.resolve(modCandidates, EnvType.CLIENT, Collections.emptyMap());

		// See net.fabricmc.loader.impl.FabricLoaderImpl#setup
		List<ModContainerImpl> modContainers = modCandidates.stream()
				.peek(modCandidate -> {
					if (!modCandidate.hasPath() && !modCandidate.isBuiltin()) {
						modCandidate.setPaths(Collections.singletonList(Paths.get(modCandidate.getId() + ".jar")));
					}
				})
				.map(ModContainerImpl::new)
				.collect(Collectors.toList());

		if (false) {
			System.out.println(modContainers);

			dumpModList(modCandidates);
		}

		return new Solution(modContainers, modCandidates);
	}

	private static ModCandidateImpl createMod(MockV1ModMetadata.Builder builder) {
		return createMod(builder, true);
	}

	private static ModCandidateImpl createMod(MockV1ModMetadata.Builder builder, boolean isRoot) {
		LoaderModMetadata metadata = builder.build();
		Collection<ModCandidateImpl> nested = builder.getNestedMods().stream()
				.map(nestedBuilder -> createMod(nestedBuilder, false))
				.collect(Collectors.toList());

		ModCandidateImpl mod = ModCandidateImpl.createPlain(
				isRoot ? Collections.singletonList(Paths.get(metadata.getId() + ".jar")) : null,
				metadata, false, nested);

		for (ModCandidateImpl modCandidate : nested) {
			modCandidate.addParent(mod);
		}

		return mod;
	}

	private static void discoverMod(List<ModCandidateImpl> mods, ModCandidateImpl modCandidate) {
		mods.add(modCandidate);

		for (ModCandidateImpl nestedMod : modCandidate.getNestedMods()) {
			discoverMod(mods, nestedMod);
		}
	}

	private static void dumpModList(List<ModCandidateImpl> mods) {
		StringBuilder modListText = new StringBuilder();

		boolean[] lastItemOfNestLevel = new boolean[mods.size()];
		List<ModCandidateImpl> topLevelMods = mods.stream()
				.filter(mod -> mod.getParentMods().isEmpty())
				.collect(Collectors.toList());
		int topLevelModsCount = topLevelMods.size();

		for (int i = 0; i < topLevelModsCount; i++) {
			boolean lastItem = i == topLevelModsCount - 1;

			if (lastItem) lastItemOfNestLevel[0] = true;

			dumpModList0(topLevelMods.get(i), modListText, 0, lastItemOfNestLevel);
		}

		int modsCount = mods.size();
		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", modsCount, modsCount != 1 ? "s" : "", modListText);
	}

	private static void dumpModList0(ModCandidateImpl mod, StringBuilder log, int nestLevel, boolean[] lastItemOfNestLevel) {
		if (log.length() > 0) log.append('\n');

		for (int depth = 0; depth < nestLevel; depth++) {
			log.append(depth == 0 ? "\t" : lastItemOfNestLevel[depth] ? "     " : "   | ");
		}

		log.append(nestLevel == 0 ? "\t" : "  ");
		log.append(nestLevel == 0 ? "-" : lastItemOfNestLevel[nestLevel] ? " \\--" : " |--");
		log.append(' ');
		log.append(mod.getId());
		log.append(' ');
		log.append(mod.getVersion().getFriendlyString());

		List<ModCandidateImpl> nestedMods = new ArrayList<>(mod.getNestedMods());
		nestedMods.sort(Comparator.comparing(nestedMod -> nestedMod.getMetadata().getId()));

		if (!nestedMods.isEmpty()) {
			Iterator<ModCandidateImpl> iterator = nestedMods.iterator();
			ModCandidateImpl nestedMod;
			boolean lastItem;

			while (iterator.hasNext()) {
				nestedMod = iterator.next();
				lastItem = !iterator.hasNext();

				if (lastItem) lastItemOfNestLevel[nestLevel+1] = true;

				dumpModList0(nestedMod, log, nestLevel + 1, lastItemOfNestLevel);

				if (lastItem) lastItemOfNestLevel[nestLevel+1] = false;
			}
		}
	}

	private static class Solution {
		List<ModContainerImpl> containers;
		List<ModCandidateImpl> candidates;

		Solution(List<ModContainerImpl> containers, List<ModCandidateImpl> candidates) {
			this.containers = containers;
			this.candidates = candidates;
		}

		boolean isModLoaded(String id) {
			for (ModContainerImpl container : containers) {
				if (container.getMetadata().getId().equals(id)) {
					return true;
				}

				for (String provides : container.getMetadata().getProvides()) {
					if (provides.equals(id)) {
						return true;
					}
				}
			}

			return false;
		}

		Version getVersion(String id) {
			for (ModContainerImpl container : containers) {
				if (container.getMetadata().getId().equals(id)) {
					return container.getMetadata().getVersion();
				}

				for (String provides : container.getMetadata().getProvides()) {
					if (provides.equals(id)) {
						return container.getMetadata().getVersion();
					}
				}
			}

			return null;
		}

		ModContainerImpl getContainer(String id) {
			for (ModContainerImpl container : containers) {
				if (container.getMetadata().getId().equals(id)) {
					return container;
				}

				for (String provides : container.getMetadata().getProvides()) {
					if (provides.equals(id)) {
						return container;
					}
				}
			}

			return null;
		}
	}
}
