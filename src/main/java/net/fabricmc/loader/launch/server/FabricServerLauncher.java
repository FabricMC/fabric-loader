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

package net.fabricmc.loader.launch.server;

import net.fabricmc.loader.util.UrlUtil;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class FabricServerLauncher {
	private static final ClassLoader parentLoader = FabricServerLauncher.class.getClassLoader();
	private static String mainClass = "net.fabricmc.loader.launch.knot.KnotServer";

	public static void main(String[] args) {
		URL propUrl = parentLoader.getResource("fabric-server-launch.properties");
		if (propUrl != null) {
			Properties properties = new Properties();
			try (InputStream is = propUrl.openStream()) {
				properties.load(is);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (properties.containsKey("launch.mainClass")) {
				mainClass = properties.getProperty("launch.mainClass");
			}
		}

		boolean dev = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));

		if (!dev) {
			try {
				setup(args);
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup Fabric server environment!", e);
			}
		} else {
			launch(mainClass, FabricServerLauncher.class.getClassLoader(), args);
		}
	}

	private static void launch(String mainClass, ClassLoader loader, String[] args) {
		try {
			Class<?> c = loader.loadClass(mainClass);
			c.getMethod("main", String[].class).invoke(null, (Object) args);
		} catch (Exception e) {
			throw new RuntimeException("An exception occurred when launching the server!", e);
		}
	}

	private static void setup(String... runArguments) throws IOException {
		// Pre-load "fabric-server-launcher.properties"
		File propertiesFile = new File("fabric-server-launcher.properties");
		Properties properties = new Properties();

		if (propertiesFile.exists()) {
			try (FileInputStream stream = new FileInputStream(propertiesFile)) {
				properties.load(stream);
			}
		}

		// Most popular Minecraft server hosting platforms do not allow
		// passing arbitrary arguments to the server .JAR. Meanwhile,
		// Mojang's default server filename is "server.jar" as of
		// a few versions... let's use this.
		if (!properties.containsKey("serverJar")) {
			properties.put("serverJar", "server.jar");
			try (FileOutputStream stream = new FileOutputStream(propertiesFile)) {
				properties.store(stream, null);
			}
		}

		File serverJar = new File((String) properties.get("serverJar"));

		if (!serverJar.exists()) {
			System.err.println("Could not find Minecraft server .JAR (" + properties.get("serverJar") + ")!");
			System.err.println();
			System.err.println("Fabric's server-side launcher expects the server .JAR to be provided.");
			System.err.println("You can edit its location in fabric-server-launcher.properties.");
			System.err.println();
			System.err.println("Without the official Minecraft server .JAR, Fabric Loader cannot launch.");
			throw new RuntimeException("Searched for '" + serverJar.getName() + "' but could not find it.");
		}

		System.setProperty("fabric.gameJarPath", serverJar.getAbsolutePath());
		try {
			URLClassLoader newClassLoader = new InjectingURLClassLoader(new URL[] { FabricServerLauncher.class.getProtectionDomain().getCodeSource().getLocation(), UrlUtil.asUrl(serverJar) }, parentLoader, "com.google.common.jimfs.");
			Thread.currentThread().setContextClassLoader(newClassLoader);
			launch(mainClass, newClassLoader, runArguments);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
