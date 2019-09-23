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

package net.fabricmc.loader.discovery;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ClasspathModCandidateFinder implements ModCandidateFinder {
	@Override
	public void findCandidates(FabricLoader loader, Consumer<URL> appender) {
		Stream<URL> urls;

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			// Search for URLs which point to 'fabric.mod.json' entries, to be considered as mods.
			try {
				Enumeration<URL> mods = FabricLauncherBase.getLauncher().getTargetClassLoader().getResources("fabric.mod.json");
				Set<URL> modsList = new HashSet<>();
				while (mods.hasMoreElements()) {
					try {
						modsList.add(UrlUtil.getSource("fabric.mod.json", mods.nextElement()));
					} catch (UrlConversionException e) {
						loader.getLogger().debug(e);
					}
				}

				// Many development environments will provide classes and resources as separate directories to the classpath.
				// As such, we're adding them to the classpath here and now.
				// To avoid tripping loader-side checks, we also don't add URLs already in modsList.
				// TODO: Perhaps a better solution would be to add the Sources of all parsed entrypoints. But this will do, for now.
				loader.getLogger().debug("[ClasspathModCandidateFinder] Adding dev classpath directories to classpath.");
				String[] classpathPropertyInput = System.getProperty("java.class.path", "").split(File.pathSeparator);
				for (String s : classpathPropertyInput) {
					if (s.isEmpty() || s.equals("*") || s.endsWith(File.separator + "*")) continue;
					File file = new File(s);
					if (file.exists() && file.isDirectory()) {
						try {
							URL url = UrlUtil.asUrl(file);
							if (!modsList.contains(url)) {
								FabricLauncherBase.getLauncher().propose(url);
							}
						} catch (UrlConversionException e) {
							loader.getLogger().warn("[ClasspathModCandidateFinder] Failed to add dev directory " + file.getAbsolutePath() + " to classpath!", e);
						}
					}
				}

				urls = modsList.stream();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			try {
				urls = Stream.of(FabricLauncherBase.getLauncher().getClass().getProtectionDomain().getCodeSource().getLocation());
			} catch (Throwable t) {
				loader.getLogger().debug("Could not fallback to itself for mod candidate lookup!", t);
				urls = Stream.empty();
			}
		}

		urls.forEach((url) -> {
			loader.getLogger().debug("[ClasspathModCandidateFinder] Processing " + url.getPath());
			File f;
			try {
				f = UrlUtil.asFile(url);
			} catch (UrlConversionException e) {
				// pass
				return;
			}

			if (f.exists()) {
				if (f.isDirectory() || f.getName().endsWith(".jar")) {
					appender.accept(url);
				}
			}
		});
	}
}
