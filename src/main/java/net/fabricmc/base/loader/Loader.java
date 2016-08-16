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

package net.fabricmc.base.loader;

import com.google.gson.*;
import net.fabricmc.base.Side;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class Loader {

	private static final Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");
	private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Side.class, new Side.Serializer()).registerTypeAdapter(ModInfo.Dependency.class, new ModInfo.Dependency.Deserializer()).registerTypeAdapter(ModInfo.Person.class, new ModInfo.Person.Deserializer()).create();
	private static final JsonParser JSON_PARSER = new JsonParser();

	private static final Map<String, ModInfo> MOD_MAP = new HashMap<>();
	private static List<ModInfo> MODS = new ArrayList<>();

	public static void load(File modsDir) {
		if (!checkModsDirectory(modsDir)) {
			return;
		}

		for (File f : modsDir.listFiles()) {
			if (f.isDirectory()) continue;
			if (!f.getPath().endsWith(".jar")) continue;

			ModInfo[] fileMods = loadMod(f);
			for (ModInfo mod : fileMods) {
				MODS.add(mod);
				MOD_MAP.put(mod.getId(), mod);
			}
		}

		checkDependencies();
		sort();
	}

	public static Set<String> getRequiredMixingConfigs() {
		return MOD_MAP.values().stream()
				.map(ModInfo::getMixinConfig)
				.filter(s -> s != null && !s.isEmpty())
				.collect(Collectors.toSet());
	}

	private static void checkDependencies() {
		for (ModInfo mod : MODS) {

			dependencies:
			for (Map.Entry<String, ModInfo.Dependency> entry : mod.getDependencies().entrySet()) {
				String depId = entry.getKey();
				ModInfo.Dependency dep = entry.getValue();
				if (dep.isRequired()) {

					innerMods:
					for (ModInfo mod2 : MODS) {
						if (mod == mod2) {
							continue innerMods;
						}
						if (depId.equalsIgnoreCase(mod2.getGroup() + "." + mod2.getId()) && dep.satisfiedBy(mod2)) {
							continue dependencies;
						}
					}
//					TODO: for official modules, query/download from maven
					throw new DependencyException(String.format("Mod %s:%s requires dependency %s @ %s", mod.getGroup(), mod.getId(), depId, String.join(", ", dep.getVersionMatchers())));
				}
			}
		}
	}

	private static void sort() {
		LinkedList<ModInfo> sorted = new LinkedList<>();
		for (ModInfo mod : MODS) {
			if (sorted.isEmpty() || mod.getDependencies().size() == 0) {
				sorted.addFirst(mod);
			} else {
				boolean b = false;
				l1:
				for (int i = 0; i < sorted.size(); i++) {
					for (Map.Entry<String, ModInfo.Dependency> entry : sorted.get(i).getDependencies().entrySet()) {
						String depId = entry.getKey();
						ModInfo.Dependency dep = entry.getValue();

						if (depId.equalsIgnoreCase(mod.getGroup() + "." + mod.getId()) && dep.satisfiedBy(mod)) {
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
		MODS = sorted;
	}

	private static boolean checkModsDirectory(File modsDir) {
		if (!modsDir.exists()) {
			modsDir.mkdirs();
			return false;
		}
		return modsDir.isDirectory();
	}

	private static ModInfo[] loadMod(File f) {
		try {
			Launch.classLoader.addURL(f.toURI().toURL());

			JarFile jar = new JarFile(f);
			ZipEntry entry = jar.getEntry("mod.json");
			if (entry != null) {
				try (InputStream in = jar.getInputStream(entry)) {
					JsonElement el = JSON_PARSER.parse(new InputStreamReader(in));
					if (el.isJsonObject()) {
						return new ModInfo[]{GSON.fromJson(el, ModInfo.class)};
					} else if (el.isJsonArray()) {
						JsonArray array = el.getAsJsonArray();
						ModInfo[] mods = new ModInfo[array.size()];
						for (int i = 0; i < array.size(); i++) {
							mods[i] = GSON.fromJson(array.get(i), ModInfo.class);
						}
						return mods;
					}
				}
			}

		} catch (Exception e) {
			LOGGER.error("Unable to load mod from %s", f.getName());
			e.printStackTrace();
		}

		return new ModInfo[0];
	}

}
