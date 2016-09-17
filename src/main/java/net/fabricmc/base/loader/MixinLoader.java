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

import net.fabricmc.base.util.Pair;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.net.MalformedURLException;
import java.util.*;

public class MixinLoader extends Loader {

	static {
		LOGGER = LogManager.getFormatterLogger("Fabric|MixinLoader");
	}

	@Override
	public void load(File modsDir) {
		if (!checkModsDirectory(modsDir)) {
			return;
		}

		List<Pair<ModInfo, File>> existingMods = new ArrayList<>();

		int classpathModsCount = 0;
		if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
			List<Pair<ModInfo, File>> classpathMods = getClasspathMods();
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
							addMod(mod, pair.getRight(), false);
						}
					}
				}
				continue mods;
			}
			addMod(mod, pair.getRight(), false);
		}

		checkDependencies();
	}

	@Override
	protected void addMod(ModInfo info, File originFile, boolean initialize) {
		ModContainer container = new ModContainer(info, originFile, initialize);
		mods.add(container);
	}
}
