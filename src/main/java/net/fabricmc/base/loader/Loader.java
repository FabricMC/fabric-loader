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
import net.fabricmc.api.Side;
import net.fabricmc.api.Stage;
import net.fabricmc.base.Fabric;
import net.fabricmc.base.util.Pair;
import net.fabricmc.base.util.SideDeserializer;
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
    public static final Loader INSTANCE = new Loader();

    protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Side.class, new SideDeserializer())
            .registerTypeAdapter(Version.class, new VersionDeserializer())
            .registerTypeAdapter(ModInfo.Dependency.class, new ModInfo.Dependency.Deserializer())
            .registerTypeAdapter(ModInfo.Person.class, new ModInfo.Person.Deserializer())
            .create();
    private static final JsonParser JSON_PARSER = new JsonParser();

    protected final Map<String, ModContainer> modMap = new HashMap<>();
    protected List<ModContainer> mods = new ArrayList<>();

    private final Stage.StageTrigger modInitStageTrigger = new Stage.StageTrigger();
    public final Stage modsInitialized = Stage.newBuilder("modsInitialized").after(modInitStageTrigger).build();

    public Set<String> getClientMixinConfigs() {
        return mods.stream()
                .map(ModContainer::getInfo)
                .map(ModInfo::getMixins)
                .map(ModInfo.Mixins::getClient)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public Set<String> getCommonMixinConfigs() {
        return mods.stream()
                .map(ModContainer::getInfo)
                .map(ModInfo::getMixins)
                .map(ModInfo.Mixins::getCommon)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void load(File modsDir) {
        if (!checkModsDirectory(modsDir)) {
            return;
        }

        load(Arrays.asList(modsDir.listFiles()));
    }

    public void load(Collection<File> modFiles) {
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

        LOGGER.debug("Found %d jar mods", existingMods.size() - classpathModsCount);

        mods:
        for (Pair<ModInfo, File> pair : existingMods) {
            ModInfo mod = pair.getLeft();
            if (mod.isLazilyLoaded()) {
                innerMods:
                for (Pair<ModInfo, File> pair2 : existingMods) {
                    ModInfo mod2 = pair2.getLeft();
                    if (mod == mod2) {
                        continue innerMods;
                    }
                    for (Map.Entry<String, ModInfo.Dependency> entry : mod2.getDependencies().entrySet()) {
                        String depId = entry.getKey();
                        ModInfo.Dependency dep = entry.getValue();
                        if (depId.equalsIgnoreCase(mod.getGroup() + "." + mod.getId()) && dep.satisfiedBy(mod)) {
                            addMod(mod, pair.getRight(), true);
                        }
                    }
                }
                continue mods;
            }
            addMod(mod, pair.getRight(), true);
        }

        LOGGER.info("Loading %d mods: %s", mods.size(), String.join(", ", mods.stream()
                .map(ModContainer::getInfo)
                .map(mod -> mod.getGroup() + "." + mod.getId())
                .collect(Collectors.toList())));

        checkDependencies();
        sort();
        initializeMods();
    }

    public boolean isModLoaded(String group, String id) {
        return modMap.containsKey(group + "." + id);
    }

    public List<ModContainer> getMods() {
        return mods;
    }

    protected static List<Pair<ModInfo, File>> getClasspathMods() {
        List<Pair<ModInfo, File>> mods = new ArrayList<>();

        String javaHome = System.getProperty("java.home");
        String modsDir = new File(Fabric.getGameDirectory(), "mods").getAbsolutePath();

        URL[] urls = Launch.classLoader.getURLs();
        for (URL url : urls) {
            if (url.getPath().startsWith(javaHome) || url.getPath().startsWith(modsDir)) {
                continue;
            }

            LOGGER.debug("Attempting to find classpath mods from " + url.getPath());
            File f = new File(url.getFile());
            if (f.exists()) {
                if (f.isDirectory()) {
                    File modJson = new File(f, "mod.json");
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

    protected void addMod(ModInfo info, File originFile, boolean initialize) {
        Side currentSide = Fabric.getSidedHandler().getSide();
        if ((currentSide == Side.CLIENT && !info.getSide().hasClient()) || (currentSide == Side.SERVER && !info.getSide().hasServer())) {
            return;
        }
        ModContainer container = new ModContainer(info, originFile, initialize);
        mods.add(container);
        modMap.put(info.getGroup() + "." + info.getId(), container);
    }

    protected void checkDependencies() {
        LOGGER.debug("Validating mod dependencies");

        for (ModContainer mod : mods) {

            dependencies:
            for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getDependencies().entrySet()) {
                String depId = entry.getKey();
                ModInfo.Dependency dep = entry.getValue();
                if (dep.isRequired()) {

                    innerMods:
                    for (ModContainer mod2 : mods) {
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

    private void sort() {
        LOGGER.debug("Sorting mods");

        LinkedList<ModContainer> sorted = new LinkedList<>();
        for (ModContainer mod : mods) {
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

        mods = sorted;
    }

    private void initializeMods() {
        for (ModContainer mod : mods) {
            if (mod.hasInstance()) {
                mod.initialize();
            }
        }

        modsInitialized.trigger(modInitStageTrigger);
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

    protected static ModInfo[] getMods(InputStream in) {
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
