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

package net.fabricmc.loader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.discovery.*;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.metadata.ModMetadataV0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The main class for mod loading operations.
 */
public class FabricLoader implements net.fabricmc.loader.api.FabricLoader {
	/**
	 * @deprecated Use {@link net.fabricmc.loader.api.FabricLoader#getInstance()} where possible,
	 * report missing areas as an issue.
	 */
	@Deprecated
	public static final FabricLoader INSTANCE = new FabricLoader();

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");

	protected final Map<String, ModContainer> modMap = new HashMap<>();
	protected List<ModContainer> mods = new ArrayList<>();

	private final InstanceStorage instanceStorage = new InstanceStorage();

	private boolean frozen = false;
	private boolean gameInitialized = false;

	private Object gameInstance;

	private File gameDir;
	private File configDir;

	/**
	 * Initializers are a way to inject the initial code which runs on the
	 * start of the game loading process without requiring a patch by each
	 * mod in question.
	 *
	 * They are added to the fabric.mod.json file, in the "initializers" array.
	 *
	 * @param type The type of the initializer class being looked for.
	 * @return The list of initialized objects for that specific class type.
	 */
	public <T> Collection<T> getInitializers(Class<T> type) {
		return instanceStorage.getInitializers(type);
	}

	protected FabricLoader() {
	}

	/**
	 * Freeze the FabricLoader, preventing additional mods from being loaded.
	 */
	public void freeze() {
		if (frozen) {
			throw new RuntimeException("Already frozen!");
		}

		frozen = true;
	}

	/**
	 * DO NOT USE. It bites.
	 */
	public void initialize(File gameDir, Object gameInstance) {
		if (gameInitialized) {
			throw new RuntimeException("FabricLoader has already been game-initialized!");
		}

		this.gameDir = gameDir;
		this.gameInstance = gameInstance;
		gameInitialized = true;
	}

	@Override
	public Object getGameInstance() {
		return gameInstance;
	}

	@Override
	public EnvType getEnvironmentType() {
		return FabricLauncherBase.getLauncher().getEnvironmentType();
	}

	/**
	 * @return The game instance's root directory.
	 */
	@Override
	public File getGameDirectory() {
		return gameDir;
	}

	/**
	 * @return The game instance's configuration directory.
	 */
	@Override
	public File getConfigDirectory() {
		if (configDir == null) {
			configDir = new File(gameDir, "config");
			if (!configDir.exists()) {
				configDir.mkdirs();
			}
		}
		return configDir;
	}

	public File getModsDirectory() {
		return new File(getGameDirectory(), "mods");
	}

	private String join(Stream<String> strings, String joiner) {
		StringBuilder builder = new StringBuilder();
		AtomicInteger i = new AtomicInteger();

		strings.sequential().forEach((s) -> {
			if ((i.getAndIncrement()) > 0) {
				builder.append(joiner);
			}

			builder.append(s);
		});

		return builder.toString();
	}

	public void load() {
		if (frozen) {
			throw new RuntimeException("Frozen - cannot load additional mods!");
		}

		ModResolver resolver = new ModResolver();
		resolver.addCandidateFinder(new ClasspathModCandidateFinder());
		resolver.addCandidateFinder(new DirectoryModCandidateFinder(getModsDirectory().toPath()));
		Map<String, ModCandidate> candidateMap;
		try {
			candidateMap = resolver.resolve(this);
		} catch (ModResolutionException e) {
			throw new RuntimeException("Failed to resolve mods!", e);
		}

		String modText;
		switch (candidateMap.values().size()) {
			case 0:
				modText = "Loading %d mods";
				break;
			case 1:
				modText = "Loading %d mod: %s";
				break;
			default:
				modText = "Loading %d mods: %s";
				break;
		}

		LOGGER.info("[" + getClass().getSimpleName() + "] " + modText, candidateMap.values().size(), candidateMap.values().stream()
			.map(info -> String.format("%s@%s", info.getInfo().getId(), info.getInfo().getVersion().getFriendlyString()))
			.collect(Collectors.joining(", ")));

		for (ModCandidate candidate : candidateMap.values()) {
			addMod(candidate, isPrimaryLoader());
		}

		onModsPopulated();
	}

	protected void onModsPopulated() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainer mod : mods) {
			FabricLauncherBase.getLauncher().propose(mod.getOriginUrl());
		}

		if (isPrimaryLoader()) {
			emitModFormatWarnings();
			initializeMods();
		}
	}

	@Override
	public Optional<net.fabricmc.loader.api.ModContainer> getModContainer(String id) {
		return Optional.ofNullable(modMap.get(id));
	}

	@Override
	public Collection<net.fabricmc.loader.api.ModContainer> getAllMods() {
		return Collections.unmodifiableList(mods);
	}

	@Override
	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLauncherBase.getLauncher().isDevelopment();
	}

	/**
	 * @return A list of all loaded mods, as ModContainers.
	 * @deprecated Use {@link net.fabricmc.loader.api.FabricLoader#getAllMods()}
	 */
	@Deprecated
	public Collection<ModContainer> getModContainers() {
		return Collections.unmodifiableList(mods);
	}

	@Deprecated
	public List<ModContainer> getMods() {
		return Collections.unmodifiableList(mods);
	}

	protected List<ModCandidate> getClasspathMods(Stream<URL> urls) {
		List<ModCandidate> mods = new ArrayList<>();

		return mods;
	}

	protected boolean isPrimaryLoader() {
		return true;
	}

	protected void addMod(ModCandidate candidate, boolean initialize) {
		LoaderModMetadata info = candidate.getInfo();
		URL originUrl = candidate.getOriginUrl();

		if (modMap.containsKey(info.getId())) {
			throw new RuntimeException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
		}

		if (!info.matchesEnvironment(getEnvironmentType())) {
			return;
		}

		ModContainer container = new ModContainer(info, originUrl);
		mods.add(container);
		modMap.put(info.getId(), container);
	}

	protected void emitModFormatWarnings() {
		for (ModContainer mod : mods) {
			if (!(mod.getInfo().getVersion() instanceof SemanticVersion)) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` does not respect SemVer - comparison support is limited.");
			} else if (((SemanticVersion) mod.getInfo().getVersion()).getVersionComponentCount() >= 4) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` uses more dot-separated version components than SemVer allows; support for this is currently not guaranteed.");
			}
		}
	}

	/* private void sortMods() {
		LOGGER.debug("Sorting mods");

		LinkedList<ModContainer> sorted = new LinkedList<>();
		for (ModContainer mod : mods) {
			if (sorted.isEmpty() || mod.getInfo().getRequires().size() == 0) {
				sorted.addFirst(mod);
			} else {
				boolean b = false;
				l1:
				for (int i = 0; i < sorted.size(); i++) {
					for (Map.Entry<String, ModMetadataV0.Dependency> entry : sorted.get(i).getInfo().getRequires().entrySet()) {
						String depId = entry.getKey();
						ModMetadataV0.Dependency dep = entry.getValue();

						if (depId.equalsIgnoreCase(mod.getInfo().getId()) && dep.satisfiedBy(mod.getInfo())) {
							sorted.add(i, mod);
							b = true;
							break l1;
						}
					}
				}

				if (!b) {
					sorted.addLast(mod);
				}
			}
		}

		mods = sorted;
	} */

	private void initializeMods() {
		for (ModContainer mod : mods) {
			try {
				mod.instantiate();

				for (String in : mod.getInfo().getInitializers()) {
					instanceStorage.instantiate(in, mod.getAdapter());
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to load mod %s (%s)", mod.getInfo().getName(), mod.getOriginUrl().getFile()), e);
			}
		}
	}

	public Logger getLogger() {
		return LOGGER;
	}
}
