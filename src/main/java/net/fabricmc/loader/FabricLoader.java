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
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.discovery.*;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.EntrypointMetadata;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.util.DefaultLanguageAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
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

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();

	private boolean frozen = false;

	private Object gameInstance;

	private File gameDir;
	private File configDir;

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
		finishModLoading();
	}

	public void setGameDir(File gameDir) {
		this.gameDir = gameDir;
		this.configDir = new File(gameDir, "config");
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
		if (!configDir.exists()) {
			configDir.mkdirs();
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
			addMod(candidate);
		}
	}

	protected void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainer mod : mods) {
			if (!mod.getInfo().getId().equals("fabricloader")) {
				FabricLauncherBase.getLauncher().propose(mod.getOriginUrl());
			}
		}

		postprocessModMetadata();
	}

	@Override
	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		return entrypointStorage.getEntrypoints(key, type);
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

	protected void addMod(ModCandidate candidate) {
		LoaderModMetadata info = candidate.getInfo();
		URL originUrl = candidate.getOriginUrl();

		if (modMap.containsKey(info.getId())) {
			throw new RuntimeException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
		}

		if (!info.loadsInEnvironment(getEnvironmentType())) {
			return;
		}

		ModContainer container = new ModContainer(info, originUrl);
		mods.add(container);
		modMap.put(info.getId(), container);
	}

	protected void postprocessModMetadata() {
		adapterMap.clear();
		adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

		for (ModContainer mod : mods) {
			if (!(mod.getInfo().getVersion() instanceof SemanticVersion)) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") does not respect SemVer - comparison support is limited.");
			} else if (((SemanticVersion) mod.getInfo().getVersion()).getVersionComponentCount() >= 4) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") uses more dot-separated version components than SemVer allows; support for this is currently not guaranteed.");
			}

			// add language adapters
			for (Map.Entry<String, String> laEntry : mod.getInfo().getLanguageAdapterDefinitions().entrySet()) {
				if (adapterMap.containsKey(laEntry.getKey())) {
					throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
				}

				try {
					adapterMap.put(laEntry.getKey(), (LanguageAdapter) Class.forName(laEntry.getValue(), true, FabricLauncherBase.getLauncher().getTargetClassLoader()).getDeclaredConstructor().newInstance());
				} catch (Exception e) {
					throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
				}
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

	public void instantiateMods(File newRunDir, Object gameInstance) {
		if (!frozen) {
			throw new RuntimeException("Cannot instantiate mods when not frozen!");
		}

		this.gameInstance = gameInstance;

		if (gameDir != null) {
			try {
				if (!gameDir.getCanonicalFile().equals(newRunDir.getCanonicalFile())) {
					getLogger().warn("Inconsistent game execution directories: engine says " + newRunDir.getAbsolutePath() + ", while initializer says " + gameDir.getAbsolutePath() + "...");
					setGameDir(newRunDir);
				}
			} catch (IOException e) {
				getLogger().warn("Exception while checking game execution directory consistency!", e);
			}
		} else {
			setGameDir(newRunDir);
		}

		for (ModContainer mod : mods) {
			try {
				mod.instantiate();

				for (String in : mod.getInfo().getOldInitializers()) {
					String adapter = mod.getInfo().getDefaultLanguageAdapter();
					entrypointStorage.addDeprecated(mod, adapter, in);
				}

				for (String key : mod.getInfo().getEntrypointKeys()) {
					for (EntrypointMetadata in : mod.getInfo().getEntrypoints(key)) {
						entrypointStorage.add(mod, key, in, adapterMap);
					}
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
