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

package net.fabricmc.loader.impl.game.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.minecraft.patch.BrandingPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatchFML125;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogHandler;

public class MinecraftGameProvider implements GameProvider {
	private static final String[] CLIENT_ENTRYPOINTS = { "net.minecraft.client.main.Main", "net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet" };
	private static final String BUNDLER_ENTRYPOINT = "net.minecraft.bundler.Main";
	private static final String[] SERVER_ENTRYPOINTS = { BUNDLER_ENTRYPOINT, "net.minecraft.server.Main", "net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer" };

	private static final String BUNDLER_MAIN_CLASS_PROPERTY = "bundlerMainClass";

	private static final String REALMS_CHECK_PATH = "realmsVersion";
	private static final String LOG4J_API_CHECK_PATH = "org/apache/logging/log4j/LogManager.class";
	private static final String[] LOG4J_IMPL_CHECK_PATHS = { "META-INF/services/org.apache.logging.log4j.spi.Provider", "META-INF/log4j-provider.properties" };
	private static final String LOG4J_CONFIG_CHECK_PATH = "log4j2.xml";
	private static final String LOG4J_PLUGIN_CHECK_PATH = "com/mojang/util/QueueLogAppender.class";

	private static final String[] ALLOWED_CLASS_PREFIXES = { "org.apache.logging.log4j.", "com.mojang.util." };

	private static final Set<String> SENSITIVE_ARGS = new HashSet<>(Arrays.asList(
			// all lowercase without --
			"accesstoken",
			"clientid",
			"profileproperties",
			"proxypass",
			"proxyuser",
			"username",
			"userproperties",
			"uuid",
			"xuid"));

	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar, realmsJar;
	private final Set<Path> log4jJars = new HashSet<>();
	private final List<Path> miscGameLibraries = new ArrayList<>(); // libraries not relevant for loader's uses
	private McVersion versionData;
	private boolean useGameJarForLogging;
	private boolean hasModLoader = false;

	private static final GameTransformer TRANSFORMER = new GameTransformer(
			new EntrypointPatch(),
			new BrandingPatch(),
			new EntrypointPatchFML125());

	@Override
	public String getGameId() {
		return "minecraft";
	}

	@Override
	public String getGameName() {
		return "Minecraft";
	}

	@Override
	public String getRawGameVersion() {
		return versionData.getRaw();
	}

	@Override
	public String getNormalizedGameVersion() {
		return versionData.getNormalized();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
				.setName(getGameName());

		if (versionData.getClassVersion().isPresent()) {
			int version = versionData.getClassVersion().getAsInt() - 44;

			try {
				metadata.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "java", Collections.singletonList(String.format(">=%d", version))));
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		return Collections.singletonList(new BuiltinMod(gameJar, metadata.build()));
	}

	public Path getGameJar() {
		return gameJar;
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return Paths.get(".");
		}

		return getLaunchDirectory(arguments);
	}

	@Override
	public boolean isObfuscated() {
		return true; // generally yes...
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return hasModLoader;
	}

	@Override
	public boolean isEnabled() {
		return System.getProperty(SystemProperties.SKIP_MC_PROVIDER) == null;
	}

	@Override
	public boolean locateGame(FabricLauncher launcher, String[] args) {
		this.envType = launcher.getEnvironmentType();
		this.arguments = new Arguments();
		arguments.parse(args);

		String[] entrypointClasses = envType == EnvType.CLIENT ? CLIENT_ENTRYPOINTS : SERVER_ENTRYPOINTS;
		Map<Path, ZipFile> zipFiles = new HashMap<>();

		try {
			String gameJarProperty = System.getProperty(SystemProperties.GAME_JAR_PATH);
			List<Path> lookupPaths;

			if (gameJarProperty != null) {
				Path path = Paths.get(gameJarProperty).toAbsolutePath().normalize();
				if (!Files.exists(path)) throw new RuntimeException("Game jar "+path+" configured through "+SystemProperties.GAME_JAR_PATH+" system property doesn't exist");

				lookupPaths = new ArrayList<>();
				lookupPaths.add(path);
				lookupPaths.addAll(launcher.getClassPath());
			} else {
				lookupPaths = launcher.getClassPath();
			}

			GameProviderHelper.FindResult result = GameProviderHelper.findFirst(lookupPaths, zipFiles, true, entrypointClasses);
			if (result == null) return false;

			if (result.name.equals(BUNDLER_ENTRYPOINT)) {
				processBundlerJar(result.path);
			} else {
				entrypoint = result.name;
				gameJar = result.path;

				result = GameProviderHelper.findFirst(lookupPaths, zipFiles, false, REALMS_CHECK_PATH);
				realmsJar = result != null && !result.path.equals(gameJar) ? result.path : null;

				result = GameProviderHelper.findFirst(lookupPaths, zipFiles, false, LOG4J_API_CHECK_PATH);
				useGameJarForLogging = result != null && gameJar.equals(result.path)
						|| Knot.class.getClassLoader().getResource(LOG4J_CONFIG_CHECK_PATH) == null;

				result = GameProviderHelper.findFirst(lookupPaths, zipFiles, true, "ModLoader");
				hasModLoader = result != null;
			}
		} finally {
			for (ZipFile zf : zipFiles.values()) {
				try {
					zf.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		if (!useGameJarForLogging && log4jJars.isEmpty()) { // use Log4J log handler directly if it is not shaded into the game jar, otherwise delay it to initialize() after deobfuscation
			setupLog4jLogHandler(launcher, false);
		}

		// expose obfuscated jar locations for mods to more easily remap code from obfuscated to intermediary
		ObjectShare share = FabricLoaderImpl.INSTANCE.getObjectShare();
		share.put("fabric-loader:inputGameJar", gameJar);
		if (realmsJar != null) share.put("fabric-loader:inputRealmsJar", realmsJar);

		String version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION);
		versionData = McVersionLookup.getVersion(gameJar, entrypointClasses, version);

		processArgumentMap(arguments, envType);

		return true;
	}

	private boolean processBundlerJar(Path path) {
		if (envType != EnvType.SERVER) return false;

		// determine urls by running the bundler and extracting them from the context class loader

		URL[] urls;

		try (URLClassLoader bundlerCl = new URLClassLoader(new URL[] { path.toUri().toURL() }, MinecraftGameProvider.class.getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					Class<?> c = findLoadedClass(name);

					if (c == null) {
						if (name.startsWith("net.minecraft.")) {
							URL url = getResource(LoaderUtil.getClassFileName(name));

							if (url != null) {
								try (InputStream is = url.openConnection().getInputStream()) {
									byte[] data = new byte[Math.max(is.available() + 1, 1000)];
									int offset = 0;
									int len;

									while ((len = is.read(data, offset, data.length - offset)) >= 0) {
										offset += len;
										if (offset == data.length) data = Arrays.copyOf(data, data.length * 2);
									}

									c = defineClass(name, data, 0, offset);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
						}

						if (c == null) {
							c = getParent().loadClass(name);
						}
					}

					if (resolve) {
						resolveClass(c);
					}

					return c;
				}
			}
		}) {
			Class<?> cls = Class.forName(BUNDLER_ENTRYPOINT, true, bundlerCl);
			Method method = cls.getMethod("main", String[].class);

			// save + restore the system property and context class loader just in case

			String prevProperty = System.getProperty(BUNDLER_MAIN_CLASS_PROPERTY);
			System.setProperty(BUNDLER_MAIN_CLASS_PROPERTY, BundlerClassPathCapture.class.getName());

			ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(bundlerCl);

			method.invoke(method, (Object) new String[0]);
			urls = BundlerClassPathCapture.FUTURE.get(10, TimeUnit.SECONDS);

			Thread.currentThread().setContextClassLoader(prevCl);

			if (prevProperty != null) {
				System.setProperty(BUNDLER_MAIN_CLASS_PROPERTY, prevProperty);
			} else {
				System.clearProperty(BUNDLER_MAIN_CLASS_PROPERTY);
			}
		} catch (ClassNotFoundException e) { // no bundler on the class path
			return false;
		} catch (Throwable t) {
			throw new RuntimeException("Error invoking MC server bundler: "+t, t);
		}

		// analyze urls to determine game/realms/log4j/misc libs and the entrypoint

		useGameJarForLogging = false;

		boolean hasGameJar = false;
		boolean hasRealmsJar = false;

		ClassLoader cl = Knot.class.getClassLoader();
		Object[] logCheckPaths = { LOG4J_API_CHECK_PATH, LOG4J_IMPL_CHECK_PATHS, LOG4J_CONFIG_CHECK_PATH, LOG4J_PLUGIN_CHECK_PATH };
		int locatedLog4jPaths = 0;

		for (int i = 0; i < logCheckPaths.length; i++) {
			Object logCheckPath = logCheckPaths[i];
			boolean found = false;

			if (logCheckPath instanceof String) {
				found = cl.getResource((String) logCheckPath) != null;
			} else {
				for (String p : (String[]) logCheckPath) {
					if (cl.getResource(p) != null) {
						found = true;
						break;
					}
				}
			}

			if (found) locatedLog4jPaths |= 1 << i;
		}

		for (URL url : urls) {
			try {
				path = UrlUtil.asPath(url);
			} catch (URISyntaxException e) {
				throw new RuntimeException("invalid url: "+url);
			}

			if (hasGameJar && hasRealmsJar && Integer.bitCount(locatedLog4jPaths) == logCheckPaths.length) {
				miscGameLibraries.add(path);
				continue;
			}

			boolean isMiscLibrary = true; // not game/realms/log4j

			try (ZipFile zf = new ZipFile(path.toFile())) {
				if (!hasGameJar) {
					for (String name : SERVER_ENTRYPOINTS) {
						if (zf.getEntry(LoaderUtil.getClassFileName(name)) != null) {
							entrypoint = name;
							gameJar = path;
							hasGameJar = true;
							isMiscLibrary = false;
							break;
						}
					}
				}

				if (!hasRealmsJar) {
					if (zf.getEntry(REALMS_CHECK_PATH) != null) {
						if (!path.equals(gameJar)) realmsJar = path;
						hasRealmsJar = true;
						isMiscLibrary = false;
					}
				}

				for (int i = 0; i < logCheckPaths.length; i++) {
					if ((locatedLog4jPaths & (1 << i)) != 0) continue;

					Object logCheckPath = logCheckPaths[i];
					boolean found = false;

					if (logCheckPath instanceof String) {
						found = zf.getEntry((String) logCheckPath) != null;
					} else {
						for (String p : (String[]) logCheckPath) {
							if (zf.getEntry(p) != null) {
								found = true;
								break;
							}
						}
					}

					if (found) {
						locatedLog4jPaths |= 1 << i;
						boolean isGameJar = path.equals(gameJar);
						useGameJarForLogging |= isGameJar;

						if (!isGameJar) {
							log4jJars.add(path);
						}

						isMiscLibrary = false;
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(String.format("Error reading %s: %s", path.toAbsolutePath(), e), e);
			}

			if (isMiscLibrary) miscGameLibraries.add(path);
		}

		if (!hasGameJar) return false;

		if ((locatedLog4jPaths & 0b11) != 0b11) { // require the first two (api+impl)
			throw new UnsupportedOperationException("MC server bundler didn't yield the Log4J API and/or implementation JARs");
		}

		hasModLoader = false; // bundler + modloader don't normally coexist

		return true;
	}

	private static void processArgumentMap(Arguments argMap, EnvType envType) {
		switch (envType) {
		case CLIENT:
			if (!argMap.containsKey("accessToken")) {
				argMap.put("accessToken", "FabricMC");
			}

			if (!argMap.containsKey("version")) {
				argMap.put("version", "Fabric");
			}

			String versionType = "";

			if (argMap.containsKey("versionType") && !argMap.get("versionType").equalsIgnoreCase("release")) {
				versionType = argMap.get("versionType") + "/";
			}

			argMap.put("versionType", versionType + "Fabric");

			if (!argMap.containsKey("gameDir")) {
				argMap.put("gameDir", getLaunchDirectory(argMap).toAbsolutePath().normalize().toString());
			}

			break;
		case SERVER:
			argMap.remove("version");
			argMap.remove("gameDir");
			argMap.remove("assetsDir");
			break;
		}
	}

	private static Path getLaunchDirectory(Arguments argMap) {
		return Paths.get(argMap.getOrDefault("gameDir", "."));
	}

	@Override
	public void initialize(FabricLauncher launcher) {
		Map<String, Path> gameJars = new HashMap<>(2);
		String name = envType.name().toLowerCase(Locale.ENGLISH);
		gameJars.put(name, gameJar);

		if (realmsJar != null) {
			gameJars.put("realms", realmsJar);
		}

		if (isObfuscated()) {
			gameJars = GameProviderHelper.deobfuscate(gameJars,
					getGameId(), getNormalizedGameVersion(),
					getLaunchDirectory(),
					launcher);

			gameJar = gameJars.get(name);
			realmsJar = gameJars.get("realms");
		}

		if (useGameJarForLogging || !log4jJars.isEmpty()) {
			if (useGameJarForLogging) {
				launcher.addToClassPath(gameJar, ALLOWED_CLASS_PREFIXES);
			}

			if (!log4jJars.isEmpty()) {
				for (Path jar : log4jJars) {
					launcher.addToClassPath(jar);
				}
			}

			setupLog4jLogHandler(launcher, true);
		}

		TRANSFORMER.locateEntrypoints(launcher, gameJar);
	}

	private void setupLog4jLogHandler(FabricLauncher launcher, boolean useTargetCl) {
		System.setProperty("log4j2.formatMsgNoLookups", "true"); // not used by mc, causes issues with older log4j2 versions

		try {
			final String logHandlerClsName = "net.fabricmc.loader.impl.game.minecraft.Log4jLogHandler";
			ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
			Class<?> logHandlerCls;

			if (useTargetCl) {
				Thread.currentThread().setContextClassLoader(launcher.getTargetClassLoader());
				logHandlerCls = launcher.loadIntoTarget(logHandlerClsName);
			} else {
				logHandlerCls = Class.forName(logHandlerClsName);
			}

			Log.init((LogHandler) logHandlerCls.getConstructor().newInstance(), true);
			Thread.currentThread().setContextClassLoader(prevCl);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Arguments getArguments() {
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments == null) return new String[0];

		String[] ret = arguments.toArray();
		if (!sanitize) return ret;

		int writeIdx = 0;

		for (int i = 0; i < ret.length; i++) {
			String arg = ret[i];

			if (i + 1 < ret.length
					&& arg.startsWith("--")
					&& SENSITIVE_ARGS.contains(arg.substring(2).toLowerCase(Locale.ENGLISH))) {
				i++; // skip value
			} else {
				ret[writeIdx++] = arg;
			}
		}

		if (writeIdx < ret.length) ret = Arrays.copyOf(ret, writeIdx);

		return ret;
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public boolean canOpenErrorGui() {
		if (arguments == null || envType == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}

	@Override
	public boolean hasAwtSupport() {
		// MC always sets -XstartOnFirstThread for LWJGL
		return !LoaderUtil.hasMacOs();
	}

	@Override
	public void unlockClassPath(FabricLauncher launcher) {
		if (useGameJarForLogging) {
			launcher.setAllowedPrefixes(gameJar);
		} else {
			launcher.addToClassPath(gameJar);
		}

		if (realmsJar != null) launcher.addToClassPath(realmsJar);

		for (Path lib : miscGameLibraries) {
			launcher.addToClassPath(lib);
		}
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		if (envType == EnvType.CLIENT && targetClass.contains("Applet")) {
			targetClass = "net.fabricmc.loader.impl.game.minecraft.applet.AppletMain";
		}

		try {
			Class<?> c = loader.loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (InvocationTargetException e) {
			throw new FormattedException("Minecraft has crashed!", e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new FormattedException("Failed to start Minecraft", e);
		}
	}
}
