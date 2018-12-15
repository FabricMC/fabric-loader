package net.fabricmc.loader.launch.common;

import net.fabricmc.api.EnvType;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CommonLauncherUtils {
	public static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");
	public static Map<String, Object> properties;
	private static FabricLauncher launcher;

	public static void withMappingReader(Consumer<BufferedReader> consumer, Runnable orElse) {
		InputStream mappingStream = CommonLauncherUtils.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");
		BufferedReader mappingReader = null;

		if (mappingStream != null) {
			mappingReader = new BufferedReader(new InputStreamReader(mappingStream));
			consumer.accept(mappingReader);

			try {
				mappingReader.close();
				mappingStream.close();
			} catch (IOException ee) {
				ee.printStackTrace();
			}
		} else {
			orElse.run();
		}
	}

	public static void deobfuscate(File gameDir, File jarFile, FabricLauncher launcher) {
		if (launcher.isDevelopment()) {
			try {
				launcher.propose(jarFile.toURI().toURL());
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}

			return;
		}

		withMappingReader((mappingReader) -> {
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
					LOGGER.info("Fabric is preparing JARs on first launch, this may take a few seconds...");

					TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(TinyUtils.createTinyMappingProvider(mappingReader, "official", "intermediary"))
						.rebuildSourceFilenames(true)
						.build();
					List<Path> depPaths = new ArrayList<>();

					try (OutputConsumerPath outputConsumer = new OutputConsumerPath(deobfJarPath)) {
						remapper.read(jarPath);

						for (URL url : launcher.getClasspathURLs()) {
							remapper.read();
							depPaths.add(new File(url.getFile()).toPath());
						}
						remapper.apply(jarPath, outputConsumer);
					} catch (IOException e) {
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

				launcher.propose(deobfJarFile.toURI().toURL());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, () -> {
			try {
				launcher.propose(jarFile.toURI().toURL());
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private CommonLauncherUtils() {

	}

	public static void processArgumentMap(Map<String, String> argMap, EnvType envType) {
		switch (envType) {
			case CLIENT:
				if (!argMap.containsKey("--accessToken")) {
					argMap.put("--accessToken", "FabricMC");
				}

				if (!argMap.containsKey("--version")) {
					argMap.put("--version", "Fabric");
				}

				String versionType = "";
				if(argMap.containsKey("--versionType") && !argMap.get("--versionType").equalsIgnoreCase("release")){
					versionType = argMap.get("--versionType") + "/";
				}
				argMap.put("--versionType", versionType + "Fabric");

				if (!argMap.containsKey("--gameDir")) {
					argMap.put("--gameDir", new File(".").getAbsolutePath());
				}
				break;
			case SERVER:
				argMap.remove("--version");
				argMap.remove("--gameDir");
				argMap.remove("--assetsDir");
				break;
		}
	}

	public static String[] asStringArray(Map<String, String> argMap) {
		String[] newArgs = new String[argMap.size() * 2];
		int i = 0;
		for (String s : argMap.keySet()) {
			newArgs[i++] = s;
			newArgs[i++] = argMap.get(s);
		}
		return newArgs;
	}

	public static void setLauncher(FabricLauncher launcherA) {
		if (launcher != null && launcher != launcherA) {
			throw new RuntimeException("Duplicate setLauncher call!");
		}

		launcher = launcherA;
	}

	public static FabricLauncher getLauncher() {
		return launcher;
	}
}
