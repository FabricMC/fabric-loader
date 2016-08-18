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

import com.github.zafarkhaja.semver.Version;
import com.google.gson.*;
import net.fabricmc.base.Side;
import net.fabricmc.base.util.VersionDeserializer;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class Loader {

    private static final Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Side.class, new Side.Serializer())
            .registerTypeAdapter(Version.class, new VersionDeserializer())
            .registerTypeAdapter(ModInfo.Dependency.class, new ModInfo.Dependency.Deserializer())
            .registerTypeAdapter(ModInfo.Person.class, new ModInfo.Person.Deserializer())
            .create();
    private static final JsonParser JSON_PARSER = new JsonParser();

    private static final Map<String, ModContainer> MOD_MAP = new HashMap<>();
    private static List<ModContainer> MODS = new ArrayList<>();

    public static void load(File modsDir) {
        if (!checkModsDirectory(modsDir)) {
            return;
        }

        List<ModInfo> existingMods = new ArrayList<>();

        int classpathModsCount = 0;
        if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
            List<ModInfo> classpathMods = getClasspathMods();
            existingMods.addAll(classpathMods);
            classpathModsCount = classpathMods.size();
            LOGGER.debug("Found %d classpath mods", classpathModsCount);
        }

        for (File f : modsDir.listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            if (!f.getPath().endsWith(".jar")) {
                continue;
            }

            ModInfo[] fileMods = getJarMods(f);

            if (fileMods.length != 0) {
                try {
                    Launch.classLoader.addURL(f.toURI().toURL());
                } catch (MalformedURLException e) {
                    LOGGER.error("Unable to load mod from %s", f.getName());
                    e.printStackTrace();
                    continue;
                }
            }

            Collections.addAll(existingMods, fileMods);
        }

        LOGGER.debug("Found %d jar mods", existingMods.size() - classpathModsCount);

        mods:
        for (ModInfo mod : existingMods) {
            if (mod.isLazilyLoaded()) {
                innerMods:
                for (ModInfo mod2 : existingMods) {
                    if (mod == mod2) {
                        continue innerMods;
                    }
                    for (Map.Entry<String, ModInfo.Dependency> entry : mod2.getDependencies().entrySet()) {
                        String depId = entry.getKey();
                        ModInfo.Dependency dep = entry.getValue();
                        if (depId.equalsIgnoreCase(mod.getGroup() + "." + mod.getId()) && dep.satisfiedBy(mod)) {
                            ModContainer container = new ModContainer(mod);
                            MODS.add(container);
                            MOD_MAP.put(mod.getGroup() + "." + mod.getId(), container);
                        }
                    }
                }
                continue mods;
            }
            ModContainer container = new ModContainer(mod);
            MODS.add(container);
            MOD_MAP.put(mod.getGroup() + "." + mod.getId(), container);
        }

        LOGGER.debug("Loading %d mods", MODS.size());

        checkDependencies();
        sort();
        initializeMods();
    }

    public static Set<String> getRequiredMixingConfigs() {
        return MOD_MAP.values().stream()
                .map(ModContainer::getInfo)
                .map(ModInfo::getMixinConfig)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public static boolean isModLoaded(String group, String id) {
        return MOD_MAP.containsKey(group + "." + id);
    }

    public static List<ModContainer> getMods() {
        return MODS;
    }

    private static List<ModInfo> getClasspathMods() {
        List<ModInfo> mods = new ArrayList<>();

        String javaHome = System.getProperty("java.home");

        URL[] urls = Launch.classLoader.getURLs();
        for (URL url : urls) {
            if (url.getPath().startsWith(javaHome)) {
                continue;
            }

            LOGGER.debug("Attempting to find classpath mods from " + url.getPath());
            File f = new File(url.getFile());
            if (f.exists()) {
                if (f.isDirectory()) {
                    File modJson = new File(f, "mod.json");
                    if (modJson.exists()) {
                        try {
                            Collections.addAll(mods, getMods(new FileInputStream(modJson)));
                        } catch (FileNotFoundException e) {
                            LOGGER.error("Unable to load mod from directory " + f.getPath());
                            e.printStackTrace();
                        }
                    }
                } else if (f.getName().endsWith(".jar")) {
                    Collections.addAll(mods, getJarMods(f));
                }
            }
        }
        return mods;
    }

    private static void checkDependencies() {
        LOGGER.debug("Validating mod dependencies");

        for (ModContainer mod : MODS) {

            dependencies:
            for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getDependencies().entrySet()) {
                String depId = entry.getKey();
                ModInfo.Dependency dep = entry.getValue();
                if (dep.isRequired()) {

                    innerMods:
                    for (ModContainer mod2 : MODS) {
                        if (mod == mod2) {
                            continue innerMods;
                        }
                        if (depId.equalsIgnoreCase(mod2.getInfo().getGroup() + "." + mod2.getInfo().getId()) && dep.satisfiedBy(mod2.getInfo())) {
                            continue dependencies;
                        }
                    }
//					TODO: for official modules, query/download from maven
                    throw new DependencyException(String.format("Mod %s.%s requires dependency %s @ %s", mod.getInfo().getGroup(), mod.getInfo().getId(), depId, String.join(", ", dep.getVersionMatchers())));
                }
            }
        }
    }

    private static void sort() {
        LOGGER.debug("Sorting mods");

        LinkedList<ModContainer> sorted = new LinkedList<>();
        for (ModContainer mod : MODS) {
            if (sorted.isEmpty() || mod.getInfo().getDependencies().size() == 0) {
                sorted.addFirst(mod);
            } else {
                boolean b = false;
                l1:
                for (int i = 0; i < sorted.size(); i++) {
                    for (Map.Entry<String, ModInfo.Dependency> entry : sorted.get(i).getInfo().getDependencies().entrySet()) {
                        String depId = entry.getKey();
                        ModInfo.Dependency dep = entry.getValue();

                        if (depId.equalsIgnoreCase(mod.getInfo().getGroup() + "." + mod.getInfo().getId()) && dep.satisfiedBy(mod.getInfo())) {
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

    private static void initializeMods() {
        for (ModContainer mod : MODS) {
            if (mod.hasModObject()) {
                mod.initialize();
            }
        }
    }

    private static boolean checkModsDirectory(File modsDir) {
        if (!modsDir.exists()) {
            modsDir.mkdirs();
            return false;
        }
        return modsDir.isDirectory();
    }

    private static ModInfo[] getJarMods(File f) {
        try {
            JarFile jar = new JarFile(f);
            ZipEntry entry = jar.getEntry("mod.json");
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

    private static ModInfo[] getMods(InputStream in) {
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

        return new ModInfo[0];
    }

}
