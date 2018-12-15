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

package net.fabricmc.loader.launch.server.stagetwo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.launch.server.FabricServerLauncher;
import net.fabricmc.loader.launch.server.InjectingURLClassLoader;
import org.apache.commons.io.FileUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FabricServerLauncherStageTwo {
	private enum Mode {
		KNOT(""),
		LAUNCHWRAPPER("launchwrapper");

		private final String name;

		Mode(String name) {
			this.name = name;
		}

		public String getJsonFilename() {
			return "fabric-installer" + (name.isEmpty() ? ".json" : "." + name + ".json");
		}
	}

	private static final File LIBRARIES = new File(".fabric/libraries");
	private static final String MAPPINGS_NAME = "net.fabricmc:yarn";
	private static final String MAPPINGS_MAVEN_META = "https://maven.modmuss50.me/net/fabricmc/yarn/maven-metadata.xml";

	// The default main class, fabric-installer.json can override this
	private static List<URL> libraries = new ArrayList<>();
	private static String mainClass = "net.fabricmc.loader.launch.knot.KnotServer";
	private static Mode mode;

	public static void stageTwo(List<String> runArguments) throws IOException {
		if (runArguments.contains("--tweakClass")) {
			mode = Mode.LAUNCHWRAPPER;
		} else {
			mode = Mode.KNOT;
		}

		//Now that we have some libs like gson we can parse the fabric-installer json
		setupFabricEnvironment(runArguments);
		setupMappings();

		Object[] objectList = runArguments.toArray();
		String[] stringArray = Arrays.copyOf(objectList, objectList.length, String[].class);
		launch(mainClass, stringArray);
	}

	private static void setupFabricEnvironment(List<String> runArguments) throws IOException {
		JsonObject installerMeta = readJson(mode.getJsonFilename());
		JsonElement mainClassElem = installerMeta.get("mainClass");
		if (mainClassElem.isJsonPrimitive()) {
			mainClass = mainClassElem.getAsString();
		} else {
			mainClass = mainClassElem.getAsJsonObject().get("server").getAsString();
		}

		String[] validSides = new String[] { "common", "server" };
		JsonObject libraries = installerMeta.getAsJsonObject("libraries");
		for (String side : validSides) {
			JsonArray librariesArray = libraries.getAsJsonArray(side);
			librariesArray.forEach(jsonElement -> setupLibrary(new Library(jsonElement)));
		}

		if (installerMeta.has("launchwrapper")) {
			String serverTweakClass = installerMeta.get("launchwrapper").getAsJsonObject().get("tweakers").getAsJsonObject().get("server").getAsString();
			runArguments.add("--tweakClass");
			runArguments.add(serverTweakClass);
		}
	}

	private static void setupLibrary(Library library) {
		if (!library.getFile().exists()) {
			System.out.println("Downloading library " + library.getURL());
			try {
				FileUtils.copyURLToFile(new URL(library.getURL()), library.getFile());
			} catch (IOException e) {
				throw new RuntimeException("Failed to download library!", e);
			}
		}

		try {
			libraries.add(library.getFile().toURI().toURL());
		} catch (MalformedURLException e) {
			throw new RuntimeException("Failed to append library to classpath!", e);
		}
	}

	private static void setupMappings() throws IOException {
		JsonObject versionMeta = readJson("version.json");
		String mcVersion = versionMeta.get("name").getAsString();

		List<String> mappingVersions;
		try {
			mappingVersions = findMappingVersionsMaven(mcVersion, MAPPINGS_MAVEN_META);
		} catch (XMLStreamException e) {
			throw new RuntimeException("Failed to parse mapping version metadata xml", e);
		}

		if (mappingVersions.isEmpty()) {
			throw new RuntimeException("No mapping versions found for " + mcVersion);
		}

		String mappingVersion = mappingVersions.get(mappingVersions.size() - 1);
		if(System.getProperty("fabric.mappings") != null){
			String mappingVersionValue = System.getProperty("fabric.mappings");
			if(!mappingVersions.contains(mappingVersionValue)){
				throw new RuntimeException(String.format("Mappings version (%s) not found", mappingVersionValue));
			}
			mappingVersion = mappingVersionValue;
		}

		setupLibrary(new Library(MAPPINGS_NAME + ":" + mappingVersion, "https://maven.modmuss50.me/"));
	}

	private static List<String> findMappingVersionsMaven(String mcVersion, String mavenMetadataUrl) throws IOException, XMLStreamException {
		List<String> versions = new ArrayList<>();
		URL url = new URL(mavenMetadataUrl); //TODO offline support?
		XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(url.openStream());
		while (reader.hasNext()) {
			if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("version")) {
				String text = reader.getElementText();
				if (text.startsWith(mcVersion + ".")) {
					versions.add(text);
				}
			}
		}
		return versions;
	}

	private static void launch(String mainClass, String[] args) {
		try {
			// If we don't add the loader here, the ClassLoader for Knot will be different...
			libraries.add(FabricServerLauncherStageTwo.class.getProtectionDomain().getCodeSource().getLocation());
			// MixinLoader needs the log4j copy from here. It will be overridden by Knot.
			libraries.add(new File(System.getProperty("fabric.gameJarPath")).toURI().toURL());

			URLClassLoader newClassLoader = new InjectingURLClassLoader(libraries.toArray(new URL[0]), ClassLoader.getSystemClassLoader());

			newClassLoader.loadClass(mainClass).getMethod("main", String[].class).invoke(null, (Object) args);
		} catch (Exception e) {
			throw new RuntimeException("An exception occurred when launching the server!", e);
		}
	}

	private static JsonObject readJson(String file) throws IOException {
		Gson gson = new Gson();
		InputStream inputStream = FabricServerLauncherStageTwo.class.getClassLoader().getResourceAsStream(file);
		Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		JsonObject installerMeta = gson.fromJson(reader, JsonObject.class);
		reader.close();
		inputStream.close();
		return installerMeta;
	}

	private static class Library {
		private String name;
		private String url = "https://libraries.minecraft.net/";

		private Library(String name, String url) {
			this.name = name;
			this.url = url;
		}

		private Library(JsonElement jsonElement) {
			JsonObject jsonObject = (JsonObject) jsonElement;
			name = jsonObject.get("name").getAsString();
			if (jsonObject.has("url")) {
				url = jsonObject.get("url").getAsString();
			}
		}

		private File getFile() {
			String[] parts = this.name.split(":", 3);
			return new File(LIBRARIES, parts[0].replace(".", File.separator) + File.separator + parts[1] + File.separator + parts[2] + File.separator + parts[1] + "-" + parts[2] + ".jar");
		}

		private String getURL() {
			String path;
			String[] parts = this.name.split(":", 3);
			path = parts[0].replace(".", "/") + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
			return url + path;
		}
	}

}
