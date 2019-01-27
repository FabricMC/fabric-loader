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

package net.fabricmc.loader.entrypoint.patches;

import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper class replacing pre-1.3 FML's ModClassLoader (which relies on
 * URLClassLoader implementation details - no longer applicable in Java 9+)
 * with an implementation effectively wrapping Knot.
 */
public class ModClassLoader_125_FML extends URLClassLoader {
	public ModClassLoader_125_FML() {
		super(new URL[0], FabricLauncherBase.getLauncher().getTargetClassLoader());
	}

	@Override
	protected void addURL(URL url) {
		FabricLauncherBase.getLauncher().propose(url);
	}

	/**
	 * This is used to add mods to the classpath.
	 * @param file The mod file.
	 * @throws MalformedURLException If the File->URL transformation fails.
	 */
	public void addFile(File file) throws MalformedURLException {
		try {
			addURL(UrlUtil.asUrl(file));
		} catch (UrlConversionException e) {
			throw new MalformedURLException(e.getMessage());
		}
	}

	/**
	 * This is used to find the Minecraft .JAR location.
	 *
	 * @return The "parent source" file.
	 */
	public File getParentSource() {
		return FabricLauncherBase.minecraftJar;
	}

	/**
	 * @return The "parent source" files array.
	 */
	public File[] getParentSources() {
		return new File[] { getParentSource() };
	}

	static {
		registerAsParallelCapable();
	}
}
