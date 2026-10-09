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

package net.fabricmc.loader.impl.game.minecraft.menu;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

final class MenuPlatform {
	private MenuPlatform() { }

	static void browse(String address) {
		try {
			URI uri = new URI(address);
			if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
			Class<?> desktop = Class.forName("java.awt.Desktop");
			desktop.getMethod("browse", URI.class).invoke(desktop.getMethod("getDesktop").invoke(null), uri);
		} catch (ReflectiveOperationException | java.net.URISyntaxException e) {
			Log.warn(LogCategory.GAME_PATCH, "Could not open a mod link", e);
		}
	}

	static void folder(Path path) {
		try {
			Files.createDirectories(path);
			Class<?> desktop = Class.forName("java.awt.Desktop");
			desktop.getMethod("open", File.class).invoke(desktop.getMethod("getDesktop").invoke(null), path.toFile());
		} catch (ReflectiveOperationException | java.io.IOException e) {
			Log.warn(LogCategory.GAME_PATCH, "Could not open the mods directory", e);
		}
	}
}
