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

package net.fabricmc.loader.impl.launch.server;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import net.fabricmc.loader.impl.launch.knot.KnotServer;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;

public class FabricServerLauncher {
	private static final ClassLoader parentLoader = FabricServerLauncher.class.getClassLoader();
	private static String mainClass = KnotServer.class.getName();

	public static void main(String[] args) {
		URL propUrl = parentLoader.getResource("fabric-server-launch.properties");

		if (propUrl != null) {
			Properties properties = new Properties();

			try (InputStreamReader reader = new InputStreamReader(propUrl.openStream(), StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (properties.containsKey("launch.mainClass")) {
				mainClass = properties.getProperty("launch.mainClass");
			}
		}

		boolean dev = SystemProperties.isSet(SystemProperties.DEVELOPMENT);

		if (!dev) {
			try {
				setup(args);
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup Fabric server environment!", e);
			}
		}

		try {
			Class<?> c = Class.forName(mainClass);
			MethodHandles.lookup().findStatic(c, "main", MethodType.methodType(void.class, String[].class)).invokeExact(args);
		} catch (Throwable e) {
			throw new RuntimeException("An exception occurred when launching the server!", e);
		}
	}

	private static void setup(String... runArguments) throws IOException {
		String path = System.getProperty(SystemProperties.GAME_JAR_PATH);

		if (path == null) {
			path = getServerJarPath();
			System.setProperty(SystemProperties.GAME_JAR_PATH, path);
		}

		Path serverJar = LoaderUtil.normalizePath(Paths.get(path));

		if (!Files.exists(serverJar)) {
			System.err.println("The Minecraft server .JAR is missing (" + serverJar + ")!");
			System.err.println();
			System.err.println("Fabric's server-side launcher expects the server .JAR to be provided.");
			System.err.println("You can edit its location in fabric-server-launcher.properties.");
			System.err.println();
			System.err.println("Without the official Minecraft server .JAR, Fabric Loader cannot launch.");
			throw new RuntimeException("Missing game jar at " + serverJar);
		}
	}

	private static String getServerJarPath() throws IOException {
		// Pre-load "fabric-server-launcher.properties"
		Path propertiesFile = Paths.get("fabric-server-launcher.properties");
		Properties properties = new Properties();

		if (Files.exists(propertiesFile)) {
			try (Reader reader = Files.newBufferedReader(propertiesFile)) {
				properties.load(reader);
			}
		}

		// Most popular Minecraft server hosting platforms do not allow
		// passing arbitrary arguments to the server .JAR. Meanwhile,
		// Mojang's default server filename is "server.jar" as of
		// a few versions... let's use this.
		if (!properties.containsKey("serverJar")) {
			properties.put("serverJar", "server.jar");

			try (Writer writer = Files.newBufferedWriter(propertiesFile)) {
				properties.store(writer, null);
			}
		}

		return (String) properties.get("serverJar");
	}
}
