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

package net.fabricmc.loader.launch;

import com.google.common.io.ByteStreams;
import com.sun.nio.zipfs.ZipFileSystem;
import com.sun.nio.zipfs.ZipFileSystemProvider;
import net.fabricmc.api.Side;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.*;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public abstract class FabricTweaker implements ITweaker {
	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Tweaker");
	protected Map<String, String> args;
	protected MixinLoader mixinLoader;

	@Override
	public void acceptOptions(List<String> localArgs, File gameDir, File assetsDir, String profile) {
		//noinspection unchecked
		this.args = (Map<String, String>) Launch.blackboard.get("launchArgs");

		if (this.args == null) {
			this.args = new HashMap<>();
			Launch.blackboard.put("launchArgs", this.args);
		}

		for (int i = 0; i < localArgs.size(); i++) {
			String arg = localArgs.get(i);
			if (arg.startsWith("--")) {
				this.args.put(arg, localArgs.get(i + 1));
				i++;
			}
		}

		if (!this.args.containsKey("--version")) {
			this.args.put("--version", profile != null ? profile : "Fabric");
		}

		if (!this.args.containsKey("--gameDir")) {
			if (gameDir == null) {
				gameDir = new File(".");
			}
			this.args.put("--gameDir", gameDir.getAbsolutePath());
		}
	}

	private void withMappingReader(LaunchClassLoader launchClassLoader, Consumer<BufferedReader> consumer, Runnable orElse) {
		String mappingFileName = args.get("--fabricMappingFile");
		FileInputStream mappingFileStream = null;
		InputStream mappingStream = null;
		BufferedReader mappingReader = null;

		if (mappingFileName != null) {
			try {
				mappingFileStream = new FileInputStream(mappingFileName);
				if (mappingFileName.toLowerCase().endsWith(".gz")) {
					mappingStream = new GZIPInputStream(mappingFileStream);
				} else {
					mappingStream = mappingFileStream;
				}
			} catch (IOException e) {
				e.printStackTrace();
				mappingStream = null;
			}
		} else {
			mappingStream = launchClassLoader.getResourceAsStream("mappings/mappings.tiny");
		}

		if (mappingStream != null) {
			mappingReader = new BufferedReader(new InputStreamReader(mappingStream));
			consumer.accept(mappingReader);

			try {
				mappingReader.close();
				mappingStream.close();
				if (mappingFileStream != mappingStream && mappingFileStream != null) {
					mappingFileStream.close();
				}
			} catch (IOException ee) {
				ee.printStackTrace();
			}
		} else {
			orElse.run();
		}
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		boolean isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		Launch.blackboard.put("fabric.development", isDevelopment);

		File gameDir = new File(args.get("--gameDir"));
		mixinLoader = new MixinLoader();
		mixinLoader.load(new File(gameDir, "mods"));

		if (isDevelopment) {
			launchClassLoader.registerTransformer("net.fabricmc.loader.transformer.PublicAccessTransformer");
		} else {
			// Obfuscated environment
			Launch.blackboard.put("fabric.development", false);

			withMappingReader(launchClassLoader, (mappingReader) -> {
				LOGGER.debug("Fabric mapping file detected, applying...");

				try {
					String target = getLaunchTarget();
					URL loc = launchClassLoader.findResource(target.replace('.', '/') + ".class");
					JarURLConnection locConn = (JarURLConnection) loc.openConnection();
					String jarFileName = locConn.getJarFileURL().getFile();
					File jarFile = new File(jarFileName);
					File deobfJarDir = new File(".fabric" + File.separator + "remappedJars");
					if (!deobfJarDir.exists()) {
						deobfJarDir.mkdirs();
					}

					File deobfJarFile = new File(deobfJarDir, jarFile.getName());

					Path jarPath = jarFile.toPath();
					Path deobfJarPath = deobfJarFile.toPath();

					if (!deobfJarFile.exists()) {
						LOGGER.info("Fabric is preparing JARs on first launch, this may take a few seconds...");

						TinyRemapper remapper = TinyRemapper.newRemapper()
							.withMappings(TinyUtils.createTinyMappingProvider(mappingReader, "official", "intermediary"))
							.rebuildSourceFilenames(true)
							.build();
						List<Path> depPaths = new ArrayList<>();

						try(OutputConsumerPath outputConsumer = new OutputConsumerPath(deobfJarPath)){
							remapper.read(jarPath);

							for (URL url : launchClassLoader.getSources()) {
								if (!url.equals(loc)) {
									remapper.read();
									depPaths.add(new File(url.getFile()).toPath());
								}
							}
							remapper.apply(jarPath, outputConsumer);
						} catch (IOException e){
							throw new RuntimeException(e);
						} finally {
							remapper.finish();
						}

						// Minecraft doesn't tend to check if a ZipFileSystem is already present,
						// so we clean up here.

						depPaths.add(jarPath);
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
					}

					launchClassLoader.addURL(deobfJarFile.toURI().toURL());

					// Pre-populate resource cache
					Map<String, byte[]> resourceCache = null;
					try {
						Field f = LaunchClassLoader.class.getDeclaredField("resourceCache");
						f.setAccessible(true);
						//noinspection unchecked
						resourceCache = (Map) f.get(launchClassLoader);
					} catch (Exception e) {
						e.printStackTrace();
					}

					if (resourceCache != null) {
						try (FileInputStream jarFileStream = new FileInputStream(deobfJarFile)) {
							JarInputStream jarStream = new JarInputStream(jarFileStream);
							JarEntry entry;

							while ((entry = jarStream.getNextJarEntry()) != null) {
								if (!entry.getName().startsWith("net/minecraft/class_") && entry.getName().endsWith(".class")) {
									String className = entry.getName();
									className = className.substring(0, className.length() - 6).replace('/', '.');
									LOGGER.debug("Appending " + className + " to resource cache...");
									resourceCache.put(className, ByteStreams.toByteArray(jarStream));
								}
							}
						}
					} else {
						LOGGER.warn("Resource cache not pre-populated - this will probably cause issues...");
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}, () -> {});
		}

		// Setup Mixin environment
		MixinBootstrap.init();
		if (isDevelopment) {
			withMappingReader(launchClassLoader,
				(reader) -> FabricMixinBootstrap.init(getSide(), args, mixinLoader, reader),
				() -> FabricMixinBootstrap.init(getSide(), args, mixinLoader));
		} else {
			FabricMixinBootstrap.init(getSide(), args, mixinLoader);
		}
		MixinEnvironment.getDefaultEnvironment().setSide(getSide() == Side.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
	}

	@Override
	public String[] getLaunchArguments() {
		List<String> launchArgs = new ArrayList<>();
		List<String> invalidPrefixes = new ArrayList<>();
		getInvalidArgPrefixes(invalidPrefixes);
		for (Map.Entry<String, String> arg : this.args.entrySet()) {
			boolean invalid = false;
			for(String prefix : invalidPrefixes){
				if(arg.getKey().startsWith(prefix)){
					invalid = true;
				}
			}
			if(invalid){
				continue;
			}
			launchArgs.add(arg.getKey());
			launchArgs.add(arg.getValue());
 		}
		return launchArgs.toArray(new String[launchArgs.size()]);
	}

	public void getInvalidArgPrefixes(List<String> list){
		list.add("--fabric");
	}

	public abstract Side getSide();
}
