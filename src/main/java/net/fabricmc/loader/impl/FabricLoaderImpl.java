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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jetbrains.annotations.VisibleForTesting;
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
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
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
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

@SuppressWarnings("deprecation")
public final class FabricLoaderImpl extends net.fabricmc.loader.FabricLoader {
	public static final FabricLoaderImpl INSTANCE = InitHelper.get();

	public static final int ASM_VERSION = Opcodes.ASM9;

	public static final String VERSION = "0.16.14";
	public static final String MOD_ID = "fabricloader";

	public static final String CACHE_DIR_NAME = ".fabric"; // relative to game dir
	private static final String PROCESSED_MODS_DIR_NAME = "processedMods"; // relative to cache dir
	public static final String REMAPPED_JARS_DIR_NAME = "remappedJars"; // relative to cache dir
	private static final String TMP_DIR_NAME = "tmp"; // relative to cache dir

	protected final Map<String, ModContainerImpl> modMap = new HashMap<>();
	private List<ModCandidateImpl> modCandidates;
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
				throw FormattedException.ofLocalized("exception.incompatible", exception.getMessage());
			} else {
				throw FormattedException.ofLocalized("exception.incompatible", exception);
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
		discoverer.addCandidateFinder(new DirectoryModCandidateFinder(getModsDirectory0(), remapRegularMods));
		discoverer.addCandidateFinder(new ArgumentModCandidateFinder(remapRegularMods));

		Map<String, Set<ModCandidateImpl>> envDisabledMods = new HashMap<>();
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

		dumpModList(modCandidates);
		dumpNonFabricMods(discoverer.getNonFabricMods());

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
				for (Iterator<ModCandidateImpl> it = modCandidates.iterator(); it.hasNext(); ) {
					ModCandidateImpl mod = it.next();

					if (mod.getId().equals(modId)) {
						it.remove();
						modCandidates.add(mod);
						break;
					}
				}
			}
		}

		// add mods

		for (ModCandidateImpl mod : modCandidates) {
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

	@VisibleForTesting
	public void dumpNonFabricMods(List<Path> nonFabricMods) {
		if (nonFabricMods.isEmpty()) return;
		StringBuilder outputText = new StringBuilder();

		for (Path nonFabricMod : nonFabricMods) {
			outputText.append("\n\t- ").append(nonFabricMod.getFileName());
		}

		int modsCount = nonFabricMods.size();
		Log.warn(LogCategory.GENERAL, "Found %d non-fabric mod%s:%s", modsCount, modsCount != 1 ? "s" : "", outputText);
	}

	private void dumpModList(List<ModCandidateImpl> mods) {
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

	private void dumpModList0(ModCandidateImpl mod, StringBuilder log, int nestLevel, boolean[] lastItemOfNestLevel) {
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
	public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
		if (!hasEntrypoints(key)) {
			Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", key);
			return;
		}

		RuntimeException exception = null;
		Collection<EntrypointContainer<T>> entrypoints = FabricLoaderImpl.INSTANCE.getEntrypointContainers(key, type);

		Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", key);

		for (EntrypointContainer<T> container : entrypoints) {
			try {
				invoker.accept(container.getEntrypoint());
			} catch (Throwable t) {
				exception = ExceptionUtil.gatherExceptions(t,
						exception,
						exc -> new RuntimeException(String.format("Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
								key, container.getProvider().getMetadata().getId(), container.getDefinition()),
								exc));
			}
		}

		if (exception != null) {
			throw exception;
		}
	}

	@Override
	public MappingResolver getMappingResolver() {
		if (mappingResolver == null) {
			final String targetNamespace = FabricLauncherBase.getLauncher().getTargetNamespace();

			mappingResolver = new LazyMappingResolver(() -> new MappingResolverImpl(
				FabricLauncherBase.getLauncher().getMappingConfiguration().getMappings(),
				targetNamespace
			), targetNamespace);
		}

		return mappingResolver;
	}

	@Override
	public ObjectShare getObjectShare() {
		return objectShare;
	}

	public ModCandidateImpl getModCandidate(String id) {
		if (modCandidates == null) return null;

		for (ModCandidateImpl mod : modCandidates) {
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

	private void addMod(ModCandidateImpl candidate) throws ModResolutionException {
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
				accessWidenerReader.read(reader, FabricLauncherBase.getLauncher().getTargetNamespace());
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

	@Override
	protected Path getModsDirectory0() {
		String directory = System.getProperty(SystemProperties.MODS_FOLDER);

		return directory != null ? Paths.get(directory) : gameDir.resolve("mods");
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
