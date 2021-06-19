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

package net.fabricmc.loader.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.discovery.ModCandidateSet;
import net.fabricmc.loader.impl.discovery.ModDiscoverer;
import net.fabricmc.loader.impl.discovery.ModResolutionException;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

@SuppressWarnings("deprecation")
public final class FabricLoaderImpl extends net.fabricmc.loader.FabricLoader {
	public static final FabricLoaderImpl INSTANCE = InitHelper.get();

	public static final int ASM_VERSION = Opcodes.ASM9;

	protected final Map<String, ModContainerImpl> modMap = new HashMap<>();
	protected List<ModContainerImpl> mods = new ArrayList<>();

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();
	private final AccessWidener accessWidener = new AccessWidener();

	private boolean frozen = false;

	private Object gameInstance;

	private MappingResolver mappingResolver;
	private GameProvider provider;
	private Path gameDir;
	private Path configDir;

	private FabricLoaderImpl() { }

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

	public GameProvider getGameProvider() {
		if (provider == null) throw new IllegalStateException("game provider not set (yet)");

		return provider;
	}

	public void setGameProvider(GameProvider provider) {
		this.provider = provider;

		setGameDir(provider.getLaunchDirectory());
	}

	private void setGameDir(Path gameDir) {
		this.gameDir = gameDir;
		this.configDir = gameDir.resolve("config");
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
	public Path getGameDir() {
		return gameDir;
	}

	@Override
	@Deprecated
	public File getGameDirectory() {
		return getGameDir().toFile();
	}

	/**
	 * @return The game instance's configuration directory.
	 */
	@Override
	public Path getConfigDir() {
		if (!Files.exists(configDir)) {
			try {
				Files.createDirectories(configDir);
			} catch (IOException e) {
				throw new RuntimeException("Creating config directory", e);
			}
		}

		return configDir;
	}

	@Override
	@Deprecated
	public File getConfigDirectory() {
		return getConfigDir().toFile();
	}

	public void load() {
		if (provider == null) throw new IllegalStateException("game provider not set");
		if (frozen) throw new IllegalStateException("Frozen - cannot load additional mods!");

		try {
			setup();
		} catch (ModResolutionException exception) {
			FabricGuiEntry.displayCriticalError(exception, true);
		}
	}

	private void setup() throws ModResolutionException {
		ModDiscoverer discoverer = new ModDiscoverer();
		discoverer.addCandidateFinder(new ClasspathModCandidateFinder());
		discoverer.addCandidateFinder(new DirectoryModCandidateFinder(gameDir.resolve("mods"), isDevelopmentEnvironment()));

		Map<String, ModCandidateSet> mods = discoverer.discoverMods(this);
		Map<String, ModCandidate> candidateMap = new ModResolver().resolve(mods);

		String modListText = candidateMap.values().stream()
				.sorted(Comparator.comparing(candidate -> candidate.getInfo().getId()))
				.map(candidate -> String.format("\t- %s@%s", candidate.getInfo().getId(), candidate.getInfo().getVersion().getFriendlyString()))
				.collect(Collectors.joining("\n"));

		int count = candidateMap.values().size();
		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", count, count != 1 ? "s" : "", modListText);

		if (DependencyOverrides.INSTANCE.getDependencyOverrides().size() > 0) {
			Log.info(LogCategory.GENERAL, "Dependencies overridden for \"%s\"", String.join(", ", DependencyOverrides.INSTANCE.getDependencyOverrides().keySet()));
		}

		boolean runtimeModRemapping = isDevelopmentEnvironment();

		if (runtimeModRemapping && System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
			Log.warn(LogCategory.MOD_REMAP, "Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.");
			runtimeModRemapping = false;
		}

		if (runtimeModRemapping) {
			for (ModCandidate candidate : RuntimeModRemapper.remap(candidateMap.values(), ModDiscoverer.getInMemoryFs())) {
				addMod(candidate);
			}
		} else {
			for (ModCandidate candidate : candidateMap.values()) {
				addMod(candidate);
			}
		}
	}

	private void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainerImpl mod : mods) {
			if (!mod.getInfo().getId().equals("fabricloader") && !mod.getInfo().getType().equals("builtin")) {
				FabricLauncherBase.getLauncher().propose(mod.getOriginUrl());
			}
		}

		postprocessModMetadata();
		setupLanguageAdapters();
		setupMods();
	}

	public boolean hasEntrypoints(String key) {
		return entrypointStorage.hasEntrypoints(key);
	}

	@Override
	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		return entrypointStorage.getEntrypoints(key, type);
	}

	@Override
	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		return entrypointStorage.getEntrypointContainers(key, type);
	}

	@Override
	public MappingResolver getMappingResolver() {
		if (mappingResolver == null) {
			mappingResolver = new MappingResolverImpl(
					FabricLauncherBase.getLauncher().getMappingConfiguration()::getMappings,
					FabricLauncherBase.getLauncher().getTargetNamespace()
					);
		}

		return mappingResolver;
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

	protected void addMod(ModCandidate candidate) throws ModResolutionException {
		LoaderModMetadata info = candidate.getInfo();
		URL originUrl = candidate.getOriginUrl();

		if (modMap.containsKey(info.getId())) {
			throw new ModResolutionException("Duplicate mod ID: %s! (%s, %s)",
					info.getId(), modMap.get(info.getId()).getOriginUrl().getFile(), originUrl.getFile());
		}

		if (!info.loadsInEnvironment(getEnvironmentType())) {
			return;
		}

		ModContainerImpl container = new ModContainerImpl(info, originUrl);
		mods.add(container);
		modMap.put(info.getId(), container);

		for (String provides : info.getProvides()) {
			if (modMap.containsKey(provides)) {
				throw new ModResolutionException("Duplicate provided alias: %s! (%s, %s)",
						provides, modMap.get(info.getId()).getOriginUrl().getFile(), originUrl.getFile());
			}

			modMap.put(provides, container);
		}
	}

	protected void postprocessModMetadata() {
		for (ModContainerImpl mod : mods) {
			if (!(mod.getInfo().getVersion() instanceof SemanticVersion)) {
				Log.warn(LogCategory.METADATA, "Mod `%s` (%s) does not respect SemVer - comparison support is limited.",
						mod.getInfo().getId(), mod.getInfo().getVersion().getFriendlyString());
			} else if (((SemanticVersion) mod.getInfo().getVersion()).getVersionComponentCount() >= 4) {
				Log.warn(LogCategory.METADATA, "Mod `%s` (%s) uses more dot-separated version components than SemVer allows; support for this is currently not guaranteed.",
						mod.getInfo().getId(), mod.getInfo().getVersion().getFriendlyString());
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

				l1: for (int i = 0; i < sorted.size(); i++) {
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

	private void setupLanguageAdapters() {
		adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

		for (ModContainerImpl mod : mods) {
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

	private void setupMods() {
		for (ModContainerImpl mod : mods) {
			try {
				for (String in : mod.getInfo().getOldInitializers()) {
					String adapter = mod.getInfo().getOldStyleLanguageAdapter();
					entrypointStorage.addDeprecated(mod, adapter, in);
				}

				for (String key : mod.getInfo().getEntrypointKeys()) {
					for (EntrypointMetadata in : mod.getInfo().getEntrypoints(key)) {
						entrypointStorage.add(mod, key, in, adapterMap);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.getInfo().getName(), mod.getOriginUrl().getFile()), e);
			}
		}
	}

	public void loadAccessWideners() {
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);

		for (net.fabricmc.loader.api.ModContainer modContainer : getAllMods()) {
			LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
			String accessWidener = modMetadata.getAccessWidener();

			if (accessWidener != null) {
				Path path = modContainer.getPath(accessWidener);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					accessWidenerReader.read(reader, getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read accessWidener file from mod " + modMetadata.getId(), e);
				}
			}
		}
	}

	public void prepareModInit(Path newRunDir, Object gameInstance) {
		if (!frozen) {
			throw new RuntimeException("Cannot instantiate mods when not frozen!");
		}

		if (gameInstance != null && FabricLauncherBase.getLauncher() instanceof Knot) {
			ClassLoader gameClassLoader = gameInstance.getClass().getClassLoader();
			ClassLoader targetClassLoader = FabricLauncherBase.getLauncher().getTargetClassLoader();
			boolean matchesKnot = (gameClassLoader == targetClassLoader);
			boolean containsKnot = false;

			if (matchesKnot) {
				containsKnot = true;
			} else {
				gameClassLoader = gameClassLoader.getParent();

				while (gameClassLoader != null && gameClassLoader.getParent() != gameClassLoader) {
					if (gameClassLoader == targetClassLoader) {
						containsKnot = true;
					}

					gameClassLoader = gameClassLoader.getParent();
				}
			}

			if (!matchesKnot) {
				if (containsKnot) {
					Log.info(LogCategory.KNOT, "Environment: Target class loader is parent of game class loader.");
				} else {
					Log.warn(LogCategory.KNOT, "\n\n* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *\n"
							+ " - Expected game class loader: %s\n"
							+ " - Actual game class loader: %s\n"
							+ "Could not find the expected class loader in game class loader parents!\n",
							FabricLauncherBase.getLauncher().getTargetClassLoader(), gameClassLoader);
				}
			}
		}

		this.gameInstance = gameInstance;

		if (gameDir != null) {
			try {
				if (!gameDir.toRealPath().equals(newRunDir.toRealPath())) {
					Log.warn(LogCategory.GENERAL, "Inconsistent game execution directories: engine says %s, while initializer says %s...",
							newRunDir.toRealPath(), gameDir.toRealPath());
					setGameDir(newRunDir);
				}
			} catch (IOException e) {
				Log.warn(LogCategory.GENERAL, "Exception while checking game execution directory consistency!", e);
			}
		} else {
			setGameDir(newRunDir);
		}
	}

	public AccessWidener getAccessWidener() {
		return accessWidener;
	}

	/**
	 * Sets the game instance. This is only used in 20w22a+ by the dedicated server and should not be called by anything else.
	 */
	public void setGameInstance(Object gameInstance) {
		if (getEnvironmentType() != EnvType.SERVER) {
			throw new UnsupportedOperationException("Cannot set game instance on a client!");
		}

		if (this.gameInstance != null) {
			throw new UnsupportedOperationException("Cannot overwrite current game instance!");
		}

		this.gameInstance = gameInstance;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		return getGameProvider().getLaunchArguments(sanitize);
	}

	/**
	 * Provides singleton for static init assignment regardless of load order.
	 */
	public static class InitHelper {
		private static FabricLoaderImpl instance;

		public static FabricLoaderImpl get() {
			if (instance == null) instance = new FabricLoaderImpl();

			return instance;
		}
	}
}
