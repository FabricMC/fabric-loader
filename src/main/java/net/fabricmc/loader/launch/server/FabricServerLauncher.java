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

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FabricServerLauncher {
	// The default main class, fabric-installer.json can override this
	private static String mainClass = "net.fabricmc.loader.launch.knot.KnotServer";

	//Launches a minecraft server along with fabric and its libs. All args are passed onto the minecraft server.
	//This expects a minecraft jar called server.jar
	public static void main(String[] args) {
		boolean dev = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		List<String> runArguments = new ArrayList<>();
		File serverJar = null;

		if (!dev) {
			for (int i = 0; i < args.length; i++) {
				if (i == 0 && !args[0].startsWith("-")) {
					serverJar = new File(args[0]);
				} else {
					runArguments.add(args[i]);
				}
			}
		} else {
			for (int i = 0; i < args.length; i++) {
				runArguments.add(args[i]);
			}
		}

		if (!dev) {
			try {
				setup(serverJar, runArguments);
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup Fabric server environment!", e);
			}
		} else {
			Object[] objectList = runArguments.toArray();
			String[] stringArray = Arrays.copyOf(objectList, objectList.length, String[].class);
			launch(mainClass, stringArray);
		}
	}

	private static void launch(String mainClass, String[] args) {
		try {
			Class.forName(mainClass).getMethod("main", String[].class).invoke(null, (Object) args);
		} catch (Exception e) {
			throw new RuntimeException("An exception occurred when launching the server!", e);
		}
	}

	private static void setup(File serverJar, List<String> runArguments) throws IOException {
		if (serverJar == null) {
			// Most popular Minecraft server hosting platforms do not allow
			// passing arbitrary arguments to the server .JAR. Meanwhile,
			// Mojang's default server filename is "server.jar" as of
			// a few versions... let's use this.
			serverJar = new File("server.jar");
		}

		if (!serverJar.exists()) {
			System.err.println("Could not find Minecraft server .JAR!");
			System.err.println("");
			System.err.println("Fabric's server-side launcher expects the server .JAR to be provided in one of the following ways:");
			System.err.println("- as the first argument to the JAR (f.e. java -jar fabric-loader.jar minecraft_server.jar),");
			System.err.println("- as the file \"server.jar\", located in the server's current working directory");
			System.err.println("  (which is usually the same directory as Fabric Loader).");
			System.err.println("");
			System.err.println("Without the official Minecraft server .JAR, Fabric Loader cannot launch.");
			throw new RuntimeException("Searched for '" + serverJar.getName() + "' but could not find it.");
		}

		System.setProperty("fabric.gameJarPath", serverJar.getAbsolutePath());
		try {
			URLClassLoader newClassLoader = new InjectingURLClassLoader(new URL[] { FabricServerLauncher.class.getProtectionDomain().getCodeSource().getLocation(), serverJar.toURI().toURL() }, FabricServerLauncher.class.getClassLoader());
			Class.forName("net.fabricmc.loader.launch.server.stagetwo.FabricServerLauncherStageTwo", true, newClassLoader)
				.getMethod("stageTwo", List.class).invoke(null, runArguments);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
