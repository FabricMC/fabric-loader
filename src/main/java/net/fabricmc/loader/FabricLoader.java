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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.CustomValue.CvArray;
import net.fabricmc.loader.api.metadata.CustomValue.CvObject;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.discovery.ModCandidate;
import net.fabricmc.loader.discovery.ModResolutionException;
import net.fabricmc.loader.discovery.ModResolver;
import net.fabricmc.loader.gui.FabricGuiEntry;
import net.fabricmc.loader.gui.FabricStatusTree;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.discovery.*;
import net.fabricmc.loader.game.GameProvider;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.knot.Knot;
import net.fabricmc.loader.metadata.EntrypointMetadata;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.metadata.ModMetadataV0.ModDependencyV0;
import net.fabricmc.loader.metadata.ModMetadataV1;
import net.fabricmc.loader.metadata.ModMetadataV1.ModDependencyV1;
import net.fabricmc.loader.util.DefaultLanguageAdapter;
import net.fabricmc.loader.util.version.VersionParsingException;
import net.fabricmc.loader.util.version.VersionPredicateParser;

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

	private MappingResolver mappingResolver;
	private GameProvider provider;
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

	public GameProvider getGameProvider() {
		if (provider == null) throw new IllegalStateException("game provider not set (yet)");

		return provider;
	}

	public void setGameProvider(GameProvider provider) {
		this.provider = provider;

		setGameDir(provider.getLaunchDirectory().toFile());
	}

	private void setGameDir(File gameDir) {
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
		if (provider == null) throw new IllegalStateException("game provider not set");
		if (frozen) throw new IllegalStateException("Frozen - cannot load additional mods!");

		FabricStatusTree tree = new FabricStatusTree();
		FabricStatusTab crashTab = tree.addTab("Crash");
		FabricStatusTab fileSystemTab = tree.addTab("File System");

		try {
			setup(fileSystemTab);
		} catch (ModResolutionException cause) {
			RuntimeException exitException = new RuntimeException("Failed to resolve mods!", cause);

			tree.mainErrorText = "Failed to launch!";
			addThrowable(crashTab.node, cause, new HashSet<>());
			try {
				FabricGuiEntry.open(true, tree, false);
			} catch (Exception guiOpeningException) {
				// If it doesn't open (for whatever reason) then the only thing we can do
				// is crash normally - as this might be a headless environment, or some
				// other strange thing happened.

				// Either way this exception isn't as important as the main exception.
				exitException.addSuppressed(guiOpeningException);
				throw exitException;
			}
			throw exitException;
		}
		tree.tabs.remove(crashTab);

		if (FabricGuiEntry.shouldShowInformationGui()) {
			// Gather all relevant information
			FabricStatusTab modsTab = tree.addTab("Mods");

			List<String> modIds = new ArrayList<>(modMap.keySet());
			modIds.sort(Comparator.naturalOrder());
			List<FabricStatusNode> modNodes = new ArrayList<>();

			for (String modId : modIds) {
				ModContainer mod = modMap.get(modId);
				LoaderModMetadata modmeta = mod.getInfo();
				FabricStatusNode modNode = modsTab.addChild(modId + " (" + modmeta.getName() + ")");
				modNodes.add(modNode);
				// TODO: Icon!
				modNode.iconType = FabricStatusTree.ICON_TYPE_FABRIC_JAR_FILE;
				modNode.addChild("ID: '" + modId + "'");
				modNode.addChild("Name: '" + modmeta.getName() + "'");
				String desc = modmeta.getDescription();
				if (desc != null && !desc.isEmpty()) {
					modNode.addChild("Description: " + desc);
				}
				modNode.addChild("Version: " + modmeta.getVersion().getFriendlyString());

				addPersonBasedInformation(modNode, "Author", modmeta.getAuthors());
				addPersonBasedInformation(modNode, "Contributor", modmeta.getContributors());
				if (!modmeta.getContact().asMap().isEmpty()) {
					addContactInfo(modNode.addChild("Contact Information"), modmeta.getContact());
				}
				if (modmeta.getLicense().isEmpty()) {
					// Note about this - licensing is *kinda* important?
					modNode.addChild("No license information!").setInfo();
				} else if (modmeta.getLicense().size() == 1) {
					modNode.addChild("License: " + modmeta.getLicense().iterator().next());
				} else {
					FabricStatusNode licenseNode = modNode.addChild("License:");
					for (String str : modmeta.getLicense()) {
						licenseNode.addChild(str);
					}
				}

				addRelatedInformation(modNode, "Dependents", "Dependent", modmeta.getDepends());
				addRelatedInformation(modNode, "Recommends", "Recommended", modmeta.getRecommends());
				addRelatedInformation(modNode, "Suggests", "Suggested", modmeta.getSuggests());
				addRelatedInformation(modNode, "Breaks", "Breaking", modmeta.getBreaks());
				addRelatedInformation(modNode, "Conflicts", "Conflicting", modmeta.getConflicts());

				// TODO: Should "getCustomKeys()" be part of the main API?
				if (modmeta instanceof ModMetadataV1) {
					Set<String> keys = ((ModMetadataV1) modmeta).getCustomKeys();
					if (!keys.isEmpty()) {
						FabricStatusNode customNode = modNode.addChild("Custom:");
						for (String key : keys) {
						    addCustomValue(customNode, key, modmeta.getCustomValue(key));
						}
					}
				}
			}

			// TODO: Class duplication detection!

			try {
				FabricGuiEntry.open(false, tree);
			} catch (Exception e) {
				// The user must have explicitly asked for this to be shown, so they probably
				// don't want to just continue loading the game even if it couldn't be shown.
				String message = "Failed to open the information GUI!"
					+ "\n(Note: You can remove the '-D" + FabricGuiEntry.OPTION_ALWAYS_SHOW_INFO
					+ "=true' argument to disable the information GUI)";
				throw new RuntimeException(message, e);
			}
		}
	}

	private static void addCustomValue(FabricStatusNode node, String key, CustomValue value) {
		switch (value.getType()) {
			case ARRAY:
				CvArray array = value.getAsArray();
				FabricStatusNode arrayNode = node.addChild(key + " = array[" + array.size() + "]");
				for (int i = 0; i < array.size(); i++) {
					addCustomValue(arrayNode, "[" + i + "]", array.get(i));
				}
				break;
			case BOOLEAN:
				node.addChild(key + " = " + value.getAsBoolean());
				break;
			case NULL:
				node.addChild(key + " = null");
				break;
			case NUMBER:
				node.addChild(key + " = " + value.getAsNumber());
				break;
			case OBJECT:
				CvObject obj = value.getAsObject();
				FabricStatusNode objNode = node.addChild(key + " = object{" + obj.size() + "}");
				for (Entry<String, CustomValue> entry : obj) {
					addCustomValue(objNode, entry.getKey(), entry.getValue());
				}
				break;
			case STRING:
				node.addChild(key + " = '" + value.getAsString() + "'");
				break;
			default:
				node.addChild(key + " = an unknwon custom type: " + value.getType());
				break;
		}
	}

	private static void addPersonBasedInformation(FabricStatusNode modNode, String name, Collection<Person> people) {
		switch (people.size()) {
			case 0:
				return;
			case 1: {
				Person person = people.iterator().next();
				FabricStatusNode node = modNode.addChild(name + ": " + person.getName());
				addContactInfo(node, person.getContact());
				return;
			}
			default: {
				FabricStatusNode peopleNode = modNode.addChild(name + "s:");
				for (Person person : people) {
					FabricStatusNode node = peopleNode.addChild(person.getName());
					addContactInfo(node, person.getContact());
				}
				return;
			}
		}
	}

	private static void addContactInfo(FabricStatusNode node, ContactInformation contact) {
		if (contact.asMap().isEmpty()) {
			return;
		}
		for (Map.Entry<String, String> entry : new TreeMap<>(contact.asMap()).entrySet()) {
			node.addChild(entry.getKey() + ": " + entry.getValue());
		}
	}

	private static void addThrowable(FabricStatusNode node, Throwable e, Set<Throwable> seen) {
		if (!seen.add(e)) {
			return;
		}

		// Remove some self-repeating exception traces from the tree
		// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
		Throwable cause;
		while ((cause = e.getCause()) != null) {
			if (e.getSuppressed().length > 0) {
				break;
			}
			if (!e.getMessage().equals(cause.getMessage()) && !e.getMessage().equals(cause.toString())) {
				break;
			}
			e = cause;
		}

		FabricStatusNode sub = node.addException(e);

		if (e.getCause() != null) {
			addThrowable(sub, e.getCause(), seen);
		}

		for (Throwable t : e.getSuppressed()) {
			addThrowable(sub, t, seen);
		}
	}

	private void addRelatedInformation(FabricStatusNode node, String section, String prefix, Collection<ModDependency> mods) {
		if (mods.isEmpty()) {
			return;
		}
		FabricStatusNode n = node.addChild(section);
		List<ModDependency> sorted = new ArrayList<>(mods);
		sorted.sort(Comparator.comparing(ModDependency::getModId));
		for (ModDependency dep : sorted) {
			FabricStatusNode submodNode = n.addChild(dep.getModId());
			ModContainer mod = modMap.get(dep.getModId());

			// Can/should this be a proper interface?
			if (dep instanceof ModDependencyV0) {
				submodNode.addChild(prefix + " versions: Anything (v0 doesn't provide exact version matching)");
			} else if (dep instanceof ModDependencyV1) {
				ModDependencyV1 depv1 = (ModDependencyV1) dep;
				List<String> versions = depv1.getMatcherStringList();
				if (versions.size() == 0) {
					FabricStatusNode matcher = submodNode.addChild(prefix + " versions: none!");
					matcher.setWarning();
				} else if (versions.size() == 1) {
					FabricStatusNode matcher = submodNode.addChild(prefix + " versions: '" + versions.get(0) + "'");
					setVersionMatchStatusV1(mod, versions.get(0), matcher);
				} else {
					FabricStatusNode allVersionsNode = submodNode.addChild(prefix + " versions: (any of these " + versions.size() + "):");
					for (String sub : versions) {
						FabricStatusNode subVersion = allVersionsNode.addChild("'" + sub + "'");
						setVersionMatchStatusV1(mod, sub, subVersion);
					}
				}
			} else {
				submodNode.addChild(prefix + " versions: unknown (" + dep.getClass() + ")");
			}

			if (mod == null) {
				FabricStatusNode loadStatus = submodNode.addChild("Not loaded");
				loadStatus.iconType = FabricStatusTree.ICON_TYPE_LESSER_CROSS;
				submodNode.iconType = FabricStatusTree.ICON_TYPE_UNKNOWN_FILE;
			} else {
				FabricStatusNode loadStatus = submodNode.addChild("Loaded version: " + mod.getMetadata().getVersion().getFriendlyString());
				loadStatus.iconType = FabricStatusTree.ICON_TYPE_TICK;
				submodNode.iconType = FabricStatusTree.ICON_TYPE_FABRIC_JAR_FILE;
			}
		}
	}

	private static void setVersionMatchStatusV1(ModContainer mod, String versionMatch, FabricStatusNode node) {
		if (mod != null) {
			boolean matches;
			try {
				matches = VersionPredicateParser.matches(mod.getInfo().getVersion(), versionMatch);
			} catch (VersionParsingException e) {
				matches = false;
				node.addException(e);
			}
			if (matches) {
				node.iconType = FabricStatusTree.ICON_TYPE_TICK;
			} else {
				node.iconType = FabricStatusTree.ICON_TYPE_LESSER_CROSS;
			}
		}
	}

	private void setup(FabricStatusTab filesystemTab) throws ModResolutionException {
		ModResolver resolver = new ModResolver();
		resolver.addCandidateFinder(new ClasspathModCandidateFinder());
		resolver.addCandidateFinder(new DirectoryModCandidateFinder(getModsDirectory().toPath()));
		Map<String, ModCandidate> candidateMap = resolver.resolve(this, filesystemTab);

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
	public MappingResolver getMappingResolver() {
		if (mappingResolver == null) {
			mappingResolver = new FabricMappingResolver(
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

	protected void addMod(ModCandidate candidate) throws ModResolutionException {
		LoaderModMetadata info = candidate.getInfo();
		URL originUrl = candidate.getOriginUrl();

		if (modMap.containsKey(info.getId())) {
			throw new ModResolutionException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
		}

		EnvType currentEnvironment = getEnvironmentType();
		if (!info.loadsInEnvironment(currentEnvironment)) {
			candidate.getFileNode().addChild("Not for this environment: " + currentEnvironment.name().toLowerCase(Locale.ROOT));
			return;
		}

		ModContainer container = new ModContainer(info, originUrl);
		mods.add(container);
		modMap.put(info.getId(), container);
	}

	protected void postprocessModMetadata() {
		for (ModContainer mod : mods) {
			if (!(mod.getInfo().getVersion() instanceof SemanticVersion)) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") does not respect SemVer - comparison support is limited.");
			} else if (((SemanticVersion) mod.getInfo().getVersion()).getVersionComponentCount() >= 4) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") uses more dot-separated version components than SemVer allows; support for this is currently not guaranteed.");
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
					getLogger().info("Environment: Target class loader is parent of game class loader.");
				} else {
					getLogger().warn("\n\n* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *\n"
						+ " - Expected game class loader: " + FabricLauncherBase.getLauncher().getTargetClassLoader() + "\n"
						+ " - Actual game class loader: " + gameClassLoader + "\n"
						+ "Could not find the expected class loader in game class loader parents!\n");
				}
			}
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

		adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

		for (ModContainer mod : mods) {
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

		for (ModContainer mod : mods) {
			try {
				mod.instantiate();

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
				throw new RuntimeException(String.format("Failed to load mod %s (%s)", mod.getInfo().getName(), mod.getOriginUrl().getFile()), e);
			}
		}
	}

	public Logger getLogger() {
		return LOGGER;
	}
}
