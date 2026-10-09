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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

final class MenuPlatform {
	private MenuPlatform() { }

	static void browse(MinecraftMenuAdapter api, String address) {
		try {
			URI uri = webUri(address);
			if (uri == null) return;
			if (!openGame(api, uri)) launch(command(System.getProperty("os.name", ""), uri.toASCIIString(), false));
		} catch (IOException | URISyntaxException | RuntimeException e) {
			Log.warn(LogCategory.GAME_PATCH, "Could not open a mod link", e);
		}
	}

	static URI webUri(String address) throws URISyntaxException {
		URI uri = new URI(address);
		return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
				&& uri.getHost() != null ? uri : null;
	}

	static void folder(MinecraftMenuAdapter api, Path path) {
		try {
			Path directory = path.toAbsolutePath().normalize();
			Files.createDirectories(directory);
			if (!openGame(api, directory.toFile())) launch(command(System.getProperty("os.name", ""), directory.toString(), true));
		} catch (IOException | RuntimeException e) {
			Log.warn(LogCategory.GAME_PATCH, "Could not open the mods directory", e);
		}
	}

	private static boolean openGame(MinecraftMenuAdapter api, Object target) {
		try {
			Class<?> util = api.load(api.gameClassLoader(), "net/minecraft/class_156", "net/minecraft/Util", "net/minecraft/util/Util");
			Method getter = api.method(util, m -> Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0,
					"method_668", "getPlatform", "getOperatingSystem");
			Object platform = getter.invoke(null);
			if (platform == null) return false;
			Method opener = api.method(platform.getClass(), m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(target),
					target instanceof URI ? "method_673" : "method_672", "openUri", "openFile", "open");
			opener.invoke(platform, target);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Log.debug(LogCategory.GAME_PATCH, "Minecraft's platform opener is unavailable", e);
			return false;
		}
	}

	static String[] command(String system, String target, boolean folder) {
		String name = system.toLowerCase(Locale.ROOT);

		if (name.startsWith("windows")) {
			return folder ? new String[] { "explorer.exe", target } : new String[] { "rundll32.exe", "url.dll,FileProtocolHandler", target };
		}

		return new String[] { name.startsWith("mac") || name.startsWith("darwin") ? "/usr/bin/open" : "xdg-open", target };
	}

	private static void launch(String[] command) throws IOException {
		new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.INHERIT)
				.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start();
	}
}
