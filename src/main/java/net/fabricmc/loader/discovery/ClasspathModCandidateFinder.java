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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ClasspathModCandidateFinder implements ModCandidateFinder {
	@Override
	public void findCandidates(FabricLoader loader, Consumer<URL> appender) {
		Stream<URL> urls;

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			try {
				Enumeration<URL> mods = FabricLauncherBase.getLauncher().getTargetClassLoader().getResources("fabric.mod.json");
				List<URL> modsList = new ArrayList<>();
				while (mods.hasMoreElements()) {
					try {
						modsList.add(UrlUtil.getSource("fabric.mod.json", mods.nextElement()));
					} catch (UrlConversionException e) {
						loader.getLogger().debug(e);
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
