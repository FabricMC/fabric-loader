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

package net.fabricmc.loader.impl.discovery;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class ClasspathModCandidateFinder implements ModCandidateFinder {
	@Override
	public void findCandidates(ModCandidateConsumer out) {
		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			// Search for URLs which point to 'fabric.mod.json' entries, to be considered as mods.
			try {
				Enumeration<URL> mods = FabricLauncherBase.getLauncher().getTargetClassLoader().getResources("fabric.mod.json");

				while (mods.hasMoreElements()) {
					try {
						out.accept(UrlUtil.getSourcePath("fabric.mod.json", mods.nextElement()), false);
					} catch (UrlConversionException e) {
						Log.debug(LogCategory.DISCOVERY, "Error determining location for fabric.mod.json", e);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else { // production, add loader as a mod
			try {
				out.accept(getFabricLoaderPath(), false);
			} catch (Throwable t) {
				Log.debug(LogCategory.DISCOVERY, "Could not retrieve launcher code source!", t);
			}
		}
	}

	public static Path getFabricLoaderPath() {
		try {
			return UrlUtil.asPath(FabricLauncherBase.getLauncher().getClass().getProtectionDomain().getCodeSource().getLocation());
		} catch (Throwable t) {
			Log.debug(LogCategory.DISCOVERY, "Could not retrieve launcher code source!", t);
			return null;
		}
	}
}
