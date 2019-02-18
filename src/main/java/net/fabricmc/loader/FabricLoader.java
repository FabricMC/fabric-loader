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

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.discovery.*;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.version.SemanticVersionImpl;
import net.fabricmc.loader.util.version.VersionDeserializer;
import org.apache.commons.logging.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

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
	private static final Pattern MOD_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{0,63}");

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
			addMod(candidate.getInfo(), candidate.getOriginUrl(), isPrimaryLoader());
		}

		onModsPopulated();
	}

	protected void onModsPopulated() {
		validateMods();
		checkDependencies();
		// sortMods();

		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainer mod : mods) {
			FabricLauncherBase.getLauncher().propose(mod.getOriginUrl());
		}

		if (isPrimaryLoader()) {
			initializeMods();
		}
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
	 */
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

	protected void addMod(ModInfo info, URL originUrl, boolean initialize) {
		if (modMap.containsKey(info.getId())) {
			throw new RuntimeException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
		}

		EnvType currentSide = getEnvironmentType();
		if ((currentSide == EnvType.CLIENT && !info.getSide().hasClient()) || (currentSide == EnvType.SERVER && !info.getSide().hasServer())) {
			return;
		}
		ModContainer container = new ModContainer(info, originUrl, initialize);
		mods.add(container);
		modMap.put(info.getId(), container);
	}

	protected void checkDependencies() {
		LOGGER.debug("Validating mod dependencies");

		for (ModContainer mod : mods) {
			dependencies:
			for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getRequires().entrySet()) {
				String depId = entry.getKey();
				ModInfo.Dependency dep = entry.getValue();
				for (ModContainer mod2 : mods) {
					if (mod == mod2) {
						continue;
					}
					if (depId.equalsIgnoreCase(mod2.getInfo().getId()) && dep.satisfiedBy(mod2.getInfo())) {
						continue dependencies;
					}
				}

				throw new DependencyException(String.format("Mod %s requires %s @ %s", mod.getInfo().getId(), depId, String.join(", ", dep.getVersionMatchers())));
			}

			conflicts:
			for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getConflicts().entrySet()) {
				String depId = entry.getKey();
				ModInfo.Dependency dep = entry.getValue();
				for (ModContainer mod2 : mods) {
					if (mod == mod2) {
						continue;
					}
					if (!depId.equalsIgnoreCase(mod2.getInfo().getId()) || !dep.satisfiedBy(mod2.getInfo())) {
						continue conflicts;
					}
				}

				throw new DependencyException(String.format("Mod %s conflicts with %s @ %s", mod.getInfo().getId(), depId, String.join(", ", dep.getVersionMatchers())));
			}
		}
	}

	protected void validateMods() {
		LOGGER.debug("Validating mods");
		for (ModContainer mod : mods) {
			if (mod.getInfo().getId() == null || mod.getInfo().getId().isEmpty()) {
				throw new RuntimeException(String.format("Mod file `%s` has no id", mod.getOriginUrl().getFile()));
			}

			if (!MOD_PATTERN.matcher(mod.getInfo().getId()).matches()) {
				throw new RuntimeException(String.format("Mod id `%s` does not match the requirements", mod.getInfo().getId()));
			}

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
					for (Map.Entry<String, ModInfo.Dependency> entry : sorted.get(i).getInfo().getRequires().entrySet()) {
						String depId = entry.getKey();
						ModInfo.Dependency dep = entry.getValue();

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
