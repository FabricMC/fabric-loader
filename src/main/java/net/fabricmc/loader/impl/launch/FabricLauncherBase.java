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
import java.util.HashSet;
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
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class FabricLauncherBase implements FabricLauncher {
	public static Path minecraftJar;

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

	protected static Path deobfuscate(String gameId, String gameVersion, Path gameDir, Path jarFile, FabricLauncher launcher) {
		if (!Files.exists(jarFile)) {
			throw new RuntimeException("Could not locate Minecraft: " + jarFile + " not found");
		}

		Log.debug(LogCategory.GAME_REMAP, "Requesting deobfuscation of %s", jarFile.getFileName());

		if (!launcher.isDevelopment()) { // in-dev is already deobfuscated
			Path deobfJarDir = gameDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve(FabricLoaderImpl.REMAPPED_JARS_DIR_NAME);

			if (!gameId.isEmpty()) {
				String versionedId = gameVersion.isEmpty() ? gameId : String.format("%s-%s", gameId, gameVersion);
				deobfJarDir = deobfJarDir.resolve(versionedId);
			}

			String targetNamespace = mappingConfiguration.getTargetNamespace();
			// TODO: allow versioning mappings?
			String deobfJarFilename = targetNamespace + "-" + jarFile.getFileName();
			Path deobfJarFile = deobfJarDir.resolve(deobfJarFilename);
			Path deobfJarFileTmp = deobfJarDir.resolve(deobfJarFilename + ".tmp");

			if (Files.exists(deobfJarFileTmp)) { // previous unfinished remap attempt
				Log.warn(LogCategory.GAME_REMAP, "Incomplete remapped file found! This means that the remapping process failed on the previous launch. If this persists, make sure to let us at Fabric know!");

				try {
					Files.deleteIfExists(deobfJarFile);
					Files.deleteIfExists(deobfJarFileTmp);
				} catch (IOException e) {
					throw new RuntimeException("can't delete incompletely remapped files", e);
				}
			}

			TinyTree mappings;

			if (!Files.exists(deobfJarFile)
					&& (mappings = mappingConfiguration.getMappings()) != null
					&& mappings.getMetadata().getNamespaces().contains(targetNamespace)) {
				Log.debug(LogCategory.GAME_REMAP, "Fabric mapping file detected, applying...");

				if (!emittedInfo) {
					Log.info(LogCategory.GAME_REMAP, "Fabric is preparing JARs on first launch, this may take a few seconds...");
					emittedInfo = true;
				}

				try {
					deobfuscate0(jarFile, deobfJarFile, deobfJarFileTmp, mappings, targetNamespace);
				} catch (IOException e) {
					throw new RuntimeException("error remapping game jar "+jarFile, e);
				}
			}

			jarFile = deobfJarFile;
		}

		launcher.addToClassPath(jarFile);

		if (minecraftJar == null) {
			minecraftJar = jarFile;
		}

		return jarFile;
	}

	private static void deobfuscate0(Path jarFile, Path deobfJarFile, Path deobfJarFileTmp, TinyTree mappings, String targetNamespace) throws IOException {
		Files.createDirectories(deobfJarFile.getParent());

		boolean found;

		do {
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

					if (!path.equals(jarFile)) {
						depPaths.add(path);
					}
				} catch (URISyntaxException e) {
					throw new RuntimeException("Failed to convert '" + url + "' to path!", e);
				}
			}

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(deobfJarFileTmp)
					// force jar despite the .tmp extension
					.assumeArchive(true)
					// don't accept class names from a blacklist of dependencies that Fabric itself utilizes
					// TODO: really could use a better solution, as always...
					.filter(clsName -> !clsName.startsWith("com/google/common/")
							&& !clsName.startsWith("com/google/gson/")
							&& !clsName.startsWith("com/google/thirdparty/")
							&& !clsName.startsWith("org/apache/logging/log4j/"))
					.build()) {
				for (Path path : depPaths) {
					Log.debug(LogCategory.GAME_REMAP, "Appending '%s' to remapper classpath", path);
					remapper.readClassPath(path);
				}

				remapper.readInputs(jarFile);
				remapper.apply(outputConsumer);
			} finally {
				remapper.finish();
			}

			// Minecraft doesn't tend to check if a ZipFileSystem is already present,
			// so we clean up here.

			depPaths.add(deobfJarFileTmp);

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

			try (JarFile jar = new JarFile(deobfJarFileTmp.toFile())) {
				found = jar.stream().anyMatch((e) -> e.getName().endsWith(".class"));
			}

			if (!found) {
				Log.error(LogCategory.GAME_REMAP, "Generated deobfuscated JAR contains no classes! Trying again...");
				Files.delete(deobfJarFileTmp);
			} else {
				Files.move(deobfJarFileTmp, deobfJarFile);
			}
		} while (!found);

		if (!Files.exists(deobfJarFile)) {
			throw new RuntimeException("Remapped .JAR file does not exist after remapping! Cannot continue!");
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
