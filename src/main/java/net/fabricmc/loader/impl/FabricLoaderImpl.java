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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.discovery.ArgumentModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.discovery.ModDiscoverer;
import net.fabricmc.loader.impl.discovery.ModResolutionException;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

@SuppressWarnings("deprecation")
public final class FabricLoaderImpl extends net.fabricmc.loader.FabricLoader {
	public static final FabricLoaderImpl INSTANCE = InitHelper.get();

	public static final int ASM_VERSION = Opcodes.ASM9;

	public static final String VERSION = "0.14.14";
	public static final String MOD_ID = "fabricloader";

	public static final String CACHE_DIR_NAME = ".fabric"; // relative to game dir
	private static final String PROCESSED_MODS_DIR_NAME = "processedMods"; // relative to cache dir
	public static final String REMAPPED_JARS_DIR_NAME = "remappedJars"; // relative to cache dir
	private static final String TMP_DIR_NAME = "tmp"; // relative to cache dir

	protected final Map<String, ModContainerImpl> modMap = new HashMap<>();
	private List<ModCandidate> modCandidates;
	protected List<ModContainerImpl> mods = new ArrayList<>();

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();
	private final AccessWidener accessWidener = new AccessWidener();

	private final ObjectShare objectShare = new ObjectShareImpl();

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
			throw new IllegalStateException("Already frozen!");
		}

		frozen = true;
		finishModLoading();
	}

	public GameProvider getGameProvider() {
		if (provider == null) throw new IllegalStateException("game provider not set (yet)");

		return provider;
	}

	public GameProvider tryGetGameProvider() {
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
		if (gameDir == null) throw new IllegalStateException("invoked too early?");

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
			if (exception.getCause() == null) {
				throw new FormattedException("Incompatible mod set!", exception.getMessage());
			} else {
				throw new FormattedException("Incompatible mod set!", exception);
			}
		}
	}

	private void setup() throws ModResolutionException {
		boolean remapRegularMods = isDevelopmentEnvironment();
		VersionOverrides versionOverrides = new VersionOverrides();
		DependencyOverrides depOverrides = new DependencyOverrides(configDir);

		// discover mods

		ModDiscoverer discoverer = new ModDiscoverer(versionOverrides, depOverrides);
		discoverer.addCandidateFinder(new ClasspathModCandidateFinder());
		discoverer.addCandidateFinder(new DirectoryModCandidateFinder(gameDir.resolve("mods"), remapRegularMods));
		discoverer.addCandidateFinder(new ArgumentModCandidateFinder(remapRegularMods));

		Map<String, Set<ModCandidate>> envDisabledMods = new HashMap<>();
		modCandidates = discoverer.discoverMods(this, envDisabledMods);

		// dump version and dependency overrides info

		if (!versionOverrides.getAffectedModIds().isEmpty()) {
			Log.info(LogCategory.GENERAL, "Versions overridden for %s", String.join(", ", versionOverrides.getAffectedModIds()));
		}

		if (!depOverrides.getAffectedModIds().isEmpty()) {
			Log.info(LogCategory.GENERAL, "Dependencies overridden for %s", String.join(", ", depOverrides.getAffectedModIds()));
		}

		// resolve mods

		modCandidates = ModResolver.resolve(modCandidates, getEnvironmentType(), envDisabledMods);

		// dump mod list

		StringBuilder modListText = new StringBuilder();

		for (ModCandidate mod : modCandidates) {
			if (modListText.length() > 0) modListText.append('\n');

			modListText.append("\t- ");
			modListText.append(mod.getId());
			modListText.append(' ');
			modListText.append(mod.getVersion().getFriendlyString());

			if (!mod.getParentMods().isEmpty()) {
				modListText.append(" via ");
				modListText.append(mod.getParentMods().iterator().next().getId());
			}
		}

		int count = modCandidates.size();
		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", count, count != 1 ? "s" : "", modListText);

		Path cacheDir = gameDir.resolve(CACHE_DIR_NAME);
		Path outputdir = cacheDir.resolve(PROCESSED_MODS_DIR_NAME);

		// runtime mod remapping

		if (remapRegularMods) {
			if (System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
				Log.warn(LogCategory.MOD_REMAP, "Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.");
			} else {
				RuntimeModRemapper.remap(modCandidates, cacheDir.resolve(TMP_DIR_NAME), outputdir);
			}
		}

		// shuffle mods in-dev to reduce the risk of false order reliance, apply late load requests

		if (isDevelopmentEnvironment() && System.getProperty(SystemProperties.DEBUG_DISABLE_MOD_SHUFFLE) == null) {
			Collections.shuffle(modCandidates);
		}

		String modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE);

		if (modsToLoadLate != null) {
			for (String modId : modsToLoadLate.split(",")) {
				for (Iterator<ModCandidate> it = modCandidates.iterator(); it.hasNext(); ) {
					ModCandidate mod = it.next();

					if (mod.getId().equals(modId)) {
						it.remove();
						modCandidates.add(mod);
						break;
					}
				}
			}
		}

		// add mods

		for (ModCandidate mod : modCandidates) {
			if (!mod.hasPath() && !mod.isBuiltin()) {
				try {
					mod.setPaths(Collections.singletonList(mod.copyToDir(outputdir, false)));
				} catch (IOException e) {
					throw new RuntimeException("Error extracting mod "+mod, e);
				}
			}

			addMod(mod);
		}

		modCandidates = null;
	}

	private void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainerImpl mod : mods) {
			if (!mod.getMetadata().getId().equals(MOD_ID) && !mod.getMetadata().getType().equals("builtin")) {
				for (Path path : mod.getCodeSourcePaths()) {
					FabricLauncherBase.getLauncher().addToClassPath(path);
				}
			}
		}

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
	public ObjectShare getObjectShare() {
		return objectShare;
	}

	public ModCandidate getModCandidate(String id) {
		if (modCandidates == null) return null;

		for (ModCandidate mod : modCandidates) {
			if (mod.getId().equals(id)) return mod;
		}

		return null;
	}

	@Override
	public Optional<net.fabricmc.loader.api.ModContainer> getModContainer(String id) {
		return Optional.ofNullable(modMap.get(id));
	}

	@Override
	public Collection<ModContainer> getAllMods() {
		return Collections.unmodifiableList(mods);
	}

	public List<ModContainerImpl> getModsInternal() {
		return mods;
	}

	@Override
	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLauncherBase.getLauncher().isDevelopment();
	}

	private void addMod(ModCandidate candidate) throws ModResolutionException {
		ModContainerImpl container = new ModContainerImpl(candidate);
		mods.add(container);
		modMap.put(candidate.getId(), container);

		for (String provides : candidate.getProvides()) {
			modMap.put(provides, container);
		}
	}

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
				throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.getInfo().getName(), mod.getOrigin()), e);
			}
		}
	}

	public void loadAccessWideners() {
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);

		for (net.fabricmc.loader.api.ModContainer modContainer : getAllMods()) {
			LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
			String accessWidener = modMetadata.getAccessWidener();
			if (accessWidener == null) continue;

			Path path = modContainer.findPath(accessWidener).orElse(null);
			if (path == null) throw new RuntimeException(String.format("Missing accessWidener file %s from mod %s", accessWidener, modContainer.getMetadata().getId()));

			try (BufferedReader reader = Files.newBufferedReader(path)) {
				accessWidenerReader.read(reader, getMappingResolver().getCurrentRuntimeNamespace());
			} catch (Exception e) {
				throw new RuntimeException("Failed to read accessWidener file from mod " + modMetadata.getId(), e);
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

	static {
		LoaderUtil.verifyNotInTargetCl(FabricLoaderImpl.class);
	}
}
