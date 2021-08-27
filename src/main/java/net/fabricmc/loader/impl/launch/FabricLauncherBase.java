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

package net.fabricmc.loader.impl.launch;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.spongepowered.asm.mixin.MixinEnvironment;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class FabricLauncherBase implements FabricLauncher {
	private static boolean mixinReady;
	private static Map<String, Object> properties;
	private static FabricLauncher launcher;
	private static MappingConfiguration mappingConfiguration = new MappingConfiguration();

	protected FabricLauncherBase() {
		setLauncher(this);
	}

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return mappingConfiguration;
	}

	private static boolean emittedInfo = false;

	protected static List<Path> deobfuscate(String gameId, String gameVersion, Path gameDir, List<Path> inputFiles, FabricLauncher launcher) {
		Log.debug(LogCategory.GAME_REMAP, "Requesting deobfuscation of %s", inputFiles);

		if (launcher.isDevelopment()) { // in-dev is already deobfuscated
			return inputFiles;
		}

		Path deobfJarDir = gameDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve(FabricLoaderImpl.REMAPPED_JARS_DIR_NAME);

		if (!gameId.isEmpty()) {
			String versionedId = gameVersion.isEmpty() ? gameId : String.format("%s-%s", gameId, gameVersion);
			deobfJarDir = deobfJarDir.resolve(versionedId);
		}

		String targetNamespace = mappingConfiguration.getTargetNamespace();
		List<Path> outputFiles = new ArrayList<>(inputFiles.size());
		List<Path> tmpFiles = new ArrayList<>(inputFiles.size());
		boolean anyMissing = false;

		for (Path inputFile : inputFiles) {
			// TODO: allow versioning mappings?
			String deobfJarFilename = targetNamespace + "-" + inputFile.getFileName();
			Path outputFile = deobfJarDir.resolve(deobfJarFilename);
			Path tmpFile = deobfJarDir.resolve(deobfJarFilename + ".tmp");

			if (Files.exists(tmpFile)) { // previous unfinished remap attempt
				Log.warn(LogCategory.GAME_REMAP, "Incomplete remapped file found! This means that the remapping process failed on the previous launch. If this persists, make sure to let us at Fabric know!");

				try {
					Files.deleteIfExists(outputFile);
					Files.deleteIfExists(tmpFile);
				} catch (IOException e) {
					throw new RuntimeException("can't delete incompletely remapped files", e);
				}
			}

			outputFiles.add(outputFile);
			tmpFiles.add(tmpFile);

			if (!anyMissing && !Files.exists(outputFile)) {
				anyMissing = true;
			}
		}

		if (!anyMissing) {
			Log.debug(LogCategory.GAME_REMAP, "Remapped files exist already, reusing them");
			return outputFiles;
		}

		TinyTree mappings = mappingConfiguration.getMappings();

		if (mappings == null
				|| !mappings.getMetadata().getNamespaces().contains(targetNamespace)) {
			Log.debug(LogCategory.GAME_REMAP, "No mappings, using input files");
			return inputFiles;
		}

		Log.debug(LogCategory.GAME_REMAP, "Fabric mapping file detected, applying...");

		if (!emittedInfo) {
			Log.info(LogCategory.GAME_REMAP, "Fabric is preparing JARs on first launch, this may take a few seconds...");
			emittedInfo = true;
		}

		try {
			Files.createDirectories(deobfJarDir);
			deobfuscate0(inputFiles, outputFiles, tmpFiles, mappings, targetNamespace);
		} catch (IOException e) {
			throw new RuntimeException("error remapping game jars "+inputFiles, e);
		}

		return outputFiles;
	}

	private static void deobfuscate0(List<Path> inputFiles, List<Path> outputFiles, List<Path> tmpFiles, TinyTree mappings, String targetNamespace) throws IOException {
		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(mappings, "official", targetNamespace))
				.rebuildSourceFilenames(true)
				.build();

		Set<Path> depPaths = new HashSet<>();

		for (URL url : launcher.getLoadTimeDependencies()) {
			try {
				Path path = UrlUtil.asPath(url);

				if (!Files.exists(path)) {
					throw new RuntimeException("Path does not exist: " + path);
				}

				if (!inputFiles.contains(path)) {
					depPaths.add(path);

					Log.debug(LogCategory.GAME_REMAP, "Appending '%s' to remapper classpath", path);
					remapper.readClassPathAsync(path);
				}
			} catch (URISyntaxException e) {
				throw new RuntimeException("Failed to convert '" + url + "' to path!", e);
			}
		}

		List<OutputConsumerPath> outputConsumers = new ArrayList<>(inputFiles.size());
		List<InputTag> inputTags = new ArrayList<>(inputFiles.size());

		try {
			for (int i = 0; i < inputFiles.size(); i++) {
				Path inputFile = inputFiles.get(i);
				Path tmpFile = tmpFiles.get(i);

				InputTag inputTag = remapper.createInputTag();
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(tmpFile)
						// force jar despite the .tmp extension
						.assumeArchive(true)
						.build();

				outputConsumers.add(outputConsumer);
				inputTags.add(inputTag);

				outputConsumer.addNonClassFiles(inputFile, NonClassCopyMode.FIX_META_INF, remapper);
				remapper.readInputsAsync(inputTag, inputFile);
			}

			for (int i = 0; i < inputFiles.size(); i++) {
				remapper.apply(outputConsumers.get(i), inputTags.get(i));
			}
		} finally {
			for (OutputConsumerPath outputConsumer : outputConsumers) {
				outputConsumer.close();
			}

			remapper.finish();
		}

		// Minecraft doesn't tend to check if a ZipFileSystem is already present,
		// so we clean up here.

		depPaths.addAll(tmpFiles);

		for (Path p : depPaths) {
			try {
				p.getFileSystem().close();
			} catch (Exception e) {
				// pass
			}

			try {
				FileSystems.getFileSystem(new URI("jar:" + p.toUri())).close();
			} catch (Exception e) {
				// pass
			}
		}

		List<Path> missing = new ArrayList<>();

		for (int i = 0; i < inputFiles.size(); i++) {
			Path inputFile = inputFiles.get(i);
			Path tmpFile = tmpFiles.get(i);
			Path outputFile = outputFiles.get(i);

			boolean found;

			try (JarFile jar = new JarFile(tmpFile.toFile())) {
				found = jar.stream().anyMatch((e) -> e.getName().endsWith(".class"));
			}

			if (!found) {
				missing.add(inputFile);
				Files.delete(tmpFile);
			} else {
				Files.move(tmpFile, outputFile);
			}
		}

		if (!missing.isEmpty()) {
			throw new RuntimeException("Generated deobfuscated JARs contain no classes: "+missing);
		}
	}

	protected static void setProperties(Map<String, Object> propertiesA) {
		if (properties != null && properties != propertiesA) {
			throw new RuntimeException("Duplicate setProperties call!");
		}

		properties = propertiesA;
	}

	private static void setLauncher(FabricLauncher launcherA) {
		if (launcher != null && launcher != launcherA) {
			throw new RuntimeException("Duplicate setLauncher call!");
		}

		launcher = launcherA;
	}

	public static FabricLauncher getLauncher() {
		return launcher;
	}

	public static Map<String, Object> getProperties() {
		return properties;
	}

	protected static void finishMixinBootstrapping() {
		if (mixinReady) {
			throw new RuntimeException("Must not call FabricLauncherBase.finishMixinBootstrapping() twice!");
		}

		try {
			Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
			m.setAccessible(true);
			m.invoke(null, MixinEnvironment.Phase.INIT);
			m.invoke(null, MixinEnvironment.Phase.DEFAULT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		mixinReady = true;
	}

	public static boolean isMixinReady() {
		return mixinReady;
	}
}
