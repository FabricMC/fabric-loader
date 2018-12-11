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
import net.fabricmc.loader.util.json.SideDeserializer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * The main class for mod loading operations.
 */
public class FabricLoader {
	public static final FabricLoader INSTANCE = new FabricLoader();

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(ModInfo.Side.class, new SideDeserializer())
		.registerTypeAdapter(ModInfo.Links.class, new ModInfo.Links.Deserializer())
		.registerTypeAdapter(ModInfo.Dependency.class, new ModInfo.Dependency.Deserializer())
		.registerTypeAdapter(ModInfo.Person.class, new ModInfo.Person.Deserializer())
		.create();
	private static final JsonParser JSON_PARSER = new JsonParser();

	protected final Map<String, ModContainer> modMap = new HashMap<>();
	protected List<ModContainer> mods = new ArrayList<>();

	private final InstanceStorage instanceStorage = new InstanceStorage();

	private boolean frozen = false;
	private boolean gameInitialized = false;

	private EnvironmentHandler environmentHandler;

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
	public void initialize(File gameDir, EnvironmentHandler sidedHandler) {
		if (gameInitialized) {
			throw new RuntimeException("FabricLoader has already been game-initialized!");
		}

		this.gameDir = gameDir;
		this.environmentHandler = sidedHandler;
		gameInitialized = true;
	}

	/**
	 * @return The environment handler for the current game instance.
	 */
	public EnvironmentHandler getEnvironmentHandler() {
		return environmentHandler;
	}

	/**
	 * @return The game instance's root directory.
	 */
	public File getGameDirectory() {
		return gameDir;
	}

	/**
	 * @return The game instance's confgiuration directory.
	 */
	public File getConfigDirectory() {
		if (configDir == null) {
			configDir = new File(gameDir, "config");
			if (!configDir.exists()) {
				configDir.mkdirs();
			}
		}
		return configDir;
	}

	public void load(File modsDir) {
		if (frozen) {
			throw new RuntimeException("Frozen - cannot load additional mods!");
		}

		if (!checkModsDirectory(modsDir)) {
			return;
		}

		File[] dirFiles = modsDir.listFiles();
		if (dirFiles != null) {
			load(Arrays.asList(dirFiles));
		}
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

	public void load(Collection<File> modFiles) {
		if (frozen) {
			throw new RuntimeException("Frozen - cannot load additional mods!");
		}

		Map<String, Set<File>> modIdSources = new HashMap<>();
		List<Pair<ModInfo, File>> existingMods = new ArrayList<>();

		int classpathModsCount = 0;
		if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
			List<Pair<ModInfo, File>> classpathMods = getClasspathMods();
			existingMods.addAll(classpathMods);
			classpathModsCount = classpathMods.size();
			LOGGER.debug("Found %d classpath mods", classpathModsCount);
		}

		for (File f : modFiles) {
			if (f.isDirectory()) {
				continue;
			}
			if (!f.getPath().endsWith(".jar")) {
				continue;
			}

			ModInfo[] fileMods = getJarMods(f);

			if (Launch.classLoader != null && fileMods.length != 0) {
				try {
					Launch.classLoader.addURL(f.toURI().toURL());
				} catch (MalformedURLException e) {
					LOGGER.error("Unable to load mod from %s", f.getName());
					e.printStackTrace();
					continue;
				}
			}

			for (ModInfo info : fileMods) {
				existingMods.add(Pair.of(info, f));
			}
		}

		LOGGER.debug("Found %d JAR mods", existingMods.size() - classpathModsCount);

		mods:
		for (Pair<ModInfo, File> pair : existingMods) {
			ModInfo mod = pair.getLeft();

			/* if (mod.isLazilyLoaded()) {
				innerMods:
				for (Pair<ModInfo, File> pair2 : existingMods) {
					ModInfo mod2 = pair2.getLeft();
					if (mod == mod2) {
						continue innerMods;
					}
					for (Map.Entry<String, ModInfo.Dependency> entry : mod2.getRequires().entrySet()) {
						String depId = entry.getKey();
						ModInfo.Dependency dep = entry.getValue();
						if (depId.equalsIgnoreCase(mod.getId()) && dep.satisfiedBy(mod)) {
							addMod(mod, pair.getRight(), loaderInitializesMods());
						}
					}
				}
				continue mods;
			} */
			addMod(mod, pair.getRight(), loaderInitializesMods());
			modIdSources.computeIfAbsent(mod.getId(), (m) -> new LinkedHashSet<>()).add(pair.getRight());
		}

		List<String> modIdsDuplicate = new ArrayList<>();
		for (String s : modIdSources.keySet()) {
			Set<File> originFiles = modIdSources.get(s);
			if (originFiles.size() >= 2) {
				modIdsDuplicate.add(s + ": " + join(originFiles.stream().map(File::getName), ", "));
			}
		}

		if (!modIdsDuplicate.isEmpty()) {
			throw new RuntimeException("Duplicate mod IDs: [" + join(modIdsDuplicate.stream(), "; ") + "]");
		}

		String modText;
		switch (mods.size()) {
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

		LOGGER.info(modText, mods.size(), String.join(", ", mods.stream()
			.map(ModContainer::getInfo)
			.map(ModInfo::getId)
			.collect(Collectors.toList())));

		onModsPopulated();
	}

	protected void onModsPopulated() {
		checkDependencies();
		sortMods();
		if (loaderInitializesMods()) {
			initializeMods();
		}
	}

	/**
	 * Check if a mod with a given ID is present.
	 * @param id The mod ID.
	 * @return True if loaded, false otherwise.
	 */
	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	/**
	 * @return A list of all loaded mods, as ModContainers.
	 */
	public List<ModContainer> getMods() {
		return Collections.unmodifiableList(mods);
	}

	protected List<Pair<ModInfo, File>> getClasspathMods() {
		List<Pair<ModInfo, File>> mods = new ArrayList<>();

		String javaHome = System.getProperty("java.home");
		String modsDir = new File(getGameDirectory(), "mods").getAbsolutePath();

		URL[] urls = Launch.classLoader.getURLs();
		for (URL url : urls) {
			if (url.getPath().startsWith(javaHome) || url.getPath().startsWith(modsDir)) {
				continue;
			}

			LOGGER.debug("Attempting to find classpath mods from " + url.getPath());
			File f;
			try {
				f = new File(url.toURI());
			} catch (URISyntaxException e) {
				// pass
				continue;
			}

			if (f.exists()) {
				if (f.isDirectory()) {
					File modJson = new File(f, "fabric.mod.json");
					if (!modJson.exists()) {
						// TODO: Remove in 0.3.0 (backwards compat)
						modJson = new File(f, "mod.json");
					}

					if (modJson.exists()) {
						try {
							for (ModInfo info : getMods(new FileInputStream(modJson))) {
								mods.add(Pair.of(info, f));
							}
						} catch (FileNotFoundException e) {
							LOGGER.error("Unable to load mod from directory " + f.getPath());
							e.printStackTrace();
						}
					}
				} else if (f.getName().endsWith(".jar")) {
					for (ModInfo info : getJarMods(f)) {
						mods.add(Pair.of(info, f));
					}
				}
			}
		}
		return mods;
	}

	protected boolean loaderInitializesMods() {
		return true;
	}

	protected void addMod(ModInfo info, File originFile, boolean initialize) {
		if (modMap.containsKey(info.getId())) {
			throw new RuntimeException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginFile().getName() + ", " + originFile.getName() + ")");
		}

		EnvType currentSide = getEnvironmentHandler().getEnvironmentType();
		if ((currentSide == EnvType.CLIENT && !info.getSide().hasClient()) || (currentSide == EnvType.SERVER && !info.getSide().hasServer())) {
			return;
		}
		ModContainer container = new ModContainer(info, originFile, initialize);
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

	private void sortMods() {
		/* LOGGER.debug("Sorting mods");

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

		mods = sorted; */
	}

	private void initializeMods() {
		for (ModContainer mod : mods) {
			for (String in : mod.getInfo().getInitializers()) {
				instanceStorage.instantiate(in, mod.getAdapter());
			}
		}
	}

	protected static boolean checkModsDirectory(File modsDir) {
		if (!modsDir.exists()) {
			modsDir.mkdirs();
			return false;
		}
		return modsDir.isDirectory();
	}

	protected static ModInfo[] getJarMods(File f) {
		try {
			JarFile jar = new JarFile(f);
			ZipEntry entry = jar.getEntry("fabric.mod.json");
			if (entry == null) {
				// TODO: Remove in 0.3.0 (backwards compat)
				entry = jar.getEntry("mod.json");
			}
			if (entry != null) {
				try (InputStream in = jar.getInputStream(entry)) {
					return getMods(in);
				}
			}

		} catch (Exception e) {
			LOGGER.error("Unable to load mod from %s", f.getName());
			e.printStackTrace();
		}

		return new ModInfo[0];
	}

	protected static ModInfo[] getMods(InputStream in) {
		JsonElement el = JSON_PARSER.parse(new InputStreamReader(in));
		if (el.isJsonObject()) {
			return new ModInfo[] { GSON.fromJson(el, ModInfo.class) };
		} else if (el.isJsonArray()) {
			JsonArray array = el.getAsJsonArray();
			ModInfo[] mods = new ModInfo[array.size()];
			for (int i = 0; i < array.size(); i++) {
				mods[i] = GSON.fromJson(array.get(i), ModInfo.class);
			}
			return mods;
		}

		return new ModInfo[0];
	}

}
