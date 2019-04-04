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

package net.fabricmc.loader.launch.common;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.util.mappings.TinyRemapperMappingsHelper;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.mappings.Mappings;
import net.fabricmc.mappings.MappingsProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public abstract class FabricLauncherBase implements FabricLauncher {
	public static File minecraftJar;

	protected static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");
	private static Map<String, Object> properties;
	private static FabricLauncher launcher;
	private static Mappings mappings;
	private static boolean checkedMappings;

	protected FabricLauncherBase() {
		setLauncher(this);
	}

	protected static File getLaunchDirectory(Arguments argMap) {
		return new File(argMap.getOrDefault("gameDir", "."));
	}

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	@Override
	public Mappings getMappings() {
		if (!checkedMappings) {
			InputStream mappingStream = FabricLauncherBase.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");

			if (mappingStream != null) {
				try {
					mappings = MappingsProvider.readTinyMappings(mappingStream);
				} catch (IOException ee) {
					ee.printStackTrace();
				}

				try {
					mappingStream.close();
				} catch (IOException ee) {
					ee.printStackTrace();
				}
			}

			if (mappings == null) {
				mappings = MappingsProvider.createEmptyMappings();
			}

			checkedMappings = true;
		}

		return mappings;
	}

	protected static void deobfuscate(File gameDir, File jarFile, FabricLauncher launcher) {
		minecraftJar = jarFile;

		Mappings mappings = launcher.isDevelopment() ? null : launcher.getMappings();
		if (mappings != null && mappings.getNamespaces().contains("intermediary")) {
			LOGGER.debug("Fabric mapping file detected, applying...");

			try {
				if (!jarFile.exists()) {
					throw new RuntimeException("Could not locate Minecraft: " + jarFile.getAbsolutePath() + " not found");
				}

				File deobfJarDir = new File(gameDir, ".fabric" + File.separator + "remappedJars");
				if (!deobfJarDir.exists()) {
					deobfJarDir.mkdirs();
				}

				File deobfJarFile = new File(deobfJarDir, jarFile.getName());

				Path jarPath = jarFile.toPath();
				Path deobfJarPath = deobfJarFile.toPath();

				if (!deobfJarFile.exists()) {
					boolean found = false;
					while (!found) {
						LOGGER.info("Fabric is preparing JARs on first launch, this may take a few seconds...");

						TinyRemapper remapper = TinyRemapper.newRemapper()
							.withMappings(TinyRemapperMappingsHelper.create(mappings, "official", "intermediary"))
							.rebuildSourceFilenames(true)
							.build();
						Set<Path> depPaths = new HashSet<>();
						depPaths.add(jarPath);

						for (URL url : launcher.getClasspathURLs()) {
							try {
								Path path = UrlUtil.asPath(url);
								if (!Files.exists(path)) {
									throw new RuntimeException("Path does not exist: " + path);
								}

								if (!path.equals(jarPath)) {
									depPaths.add(path);
								}
							} catch (UrlConversionException e) {
								throw new RuntimeException(e);
							}
						}

						try (OutputConsumerPath outputConsumer = new OutputConsumerPath(deobfJarPath)) {
							for (Path path : depPaths) {
								LOGGER.debug("Appending '" + path + "' to remapper classpath");
								remapper.read(path);
							}
							remapper.apply(jarPath, outputConsumer);
						} catch (IOException e) {
							throw new RuntimeException(e);
						} finally {
							remapper.finish();
						}

						// Minecraft doesn't tend to check if a ZipFileSystem is already present,
						// so we clean up here.

						depPaths.add(deobfJarPath);
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

						JarFile jar = new JarFile(deobfJarFile);
						if (jar.stream().noneMatch((e) -> e.getName().endsWith(".class"))) {
							LOGGER.error("Generated deobfuscated JAR contains no classes! Trying again...");
							Files.delete(deobfJarPath);
						} else {
							found = true;
						}
					}
				}

				launcher.propose(UrlUtil.asUrl(deobfJarFile));
				launcher.proposeJarClasspaths(minecraftJar);
				minecraftJar = deobfJarFile;
			} catch (IOException | UrlConversionException e) {
				throw new RuntimeException(e);
			}
		} else {
			try {
				launcher.propose(UrlUtil.asUrl(jarFile));
				minecraftJar = jarFile;
			} catch (UrlConversionException e) {
				throw new RuntimeException(e);
			}
		}
	}

	protected static void processArgumentMap(Arguments argMap, EnvType envType) {
		switch (envType) {
			case CLIENT:
				if (!argMap.containsKey("accessToken")) {
					argMap.put("accessToken", "FabricMC");
				}

				if (!argMap.containsKey("version")) {
					argMap.put("version", "Fabric");
				}

				String versionType = "";
				if(argMap.containsKey("versionType") && !argMap.get("versionType").equalsIgnoreCase("release")){
					versionType = argMap.get("versionType") + "/";
				}
				argMap.put("versionType", versionType + "Fabric");

				if (!argMap.containsKey("gameDir")) {
					argMap.put("gameDir", getLaunchDirectory(argMap).getAbsolutePath());
				}
				break;
			case SERVER:
				argMap.remove("version");
				argMap.remove("gameDir");
				argMap.remove("assetsDir");
				break;
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

	protected static void pretendMixinPhases() {
		try {
			Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
			m.setAccessible(true);
			m.invoke(null, MixinEnvironment.Phase.INIT);
			m.invoke(null, MixinEnvironment.Phase.DEFAULT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
