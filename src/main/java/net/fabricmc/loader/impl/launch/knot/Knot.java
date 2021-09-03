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

package net.fabricmc.loader.impl.launch.knot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loader.impl.entrypoint.MixinLoadingEntrypoint;

import org.spongepowered.asm.launch.MixinBootstrap;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.FabricMixinBootstrap;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class Knot extends FabricLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private KnotClassLoaderInterface classLoader;
	private boolean isDevelopment;
	private EnvType envType;
	private final File gameJarFile;
	private GameProvider provider;

	public static void launch(String[] args, EnvType type) {
		String gameJarPath = System.getProperty(SystemProperties.GAME_JAR_PATH);
		Knot knot = new Knot(type, gameJarPath != null ? new File(gameJarPath) : null);
		ClassLoader cl = knot.init(args);

		if (knot.provider == null) {
			throw new IllegalStateException("Game provider was not initialized! (Knot#init(String[]))");
		}

		knot.provider.launch(cl);
	}

	public Knot(EnvType type, File gameJarFile) {
		this.envType = type;
		this.gameJarFile = gameJarFile;
	}

	protected ClassLoader init(String[] args) {
		setProperties(properties);

		// configure fabric vars
		if (envType == null) {
			String side = System.getProperty(SystemProperties.SIDE);
			if (side == null) throw new RuntimeException("Please specify side or use a dedicated Knot!");

			switch (side.toLowerCase(Locale.ROOT)) {
			case "client":
				envType = EnvType.CLIENT;
				break;
			case "server":
				envType = EnvType.SERVER;
				break;
			default:
				throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
			}
		}

		provider = createGameProvider(envType, args);
		Log.info(LogCategory.GAME_PROVIDER, "Loading for game %s %s", provider.getGameName(), provider.getRawGameVersion());

		isDevelopment = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));

		// Setup classloader
		// TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
		boolean useCompatibility = provider.requiresUrlClassLoader() || Boolean.parseBoolean(System.getProperty("fabric.loader.useCompatibilityClassLoader", "false"));
		classLoader = useCompatibility ? new KnotCompatibilityClassLoader(isDevelopment(), envType, provider) : new KnotClassLoader(isDevelopment(), envType, provider);
		ClassLoader cl = (ClassLoader) classLoader;

		if (provider.isObfuscated()) {
			for (Path path : provider.getGameContextJars()) {
				FabricLauncherBase.deobfuscate(
						provider.getGameId(), provider.getNormalizedGameVersion(),
						provider.getLaunchDirectory(),
						path,
						this);
			}
		}

		// Locate entrypoints before switching class loaders
		provider.getEntrypointTransformer().locateEntrypoints(this);

		Thread.currentThread().setContextClassLoader(cl);

		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		loader.freeze();
		loader.loadAccessWideners();

		// Some mods has any API required for mixins. But they most-likely load it remotely, since its unused in any other place.
		EntrypointUtils.invoke("onMixinLoading", MixinLoadingEntrypoint.class, MixinLoadingEntrypoint::onMixinLoading);

		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), loader);
		FabricLauncherBase.finishMixinBootstrapping();

		classLoader.getDelegate().initializeTransformers();

		EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);

		return cl;
	}

	private static GameProvider createGameProvider(EnvType envType, String[] args) {
		// fast path with direct lookup

		GameProvider embeddedGameProvider = findEmbedddedGameProvider();
		ClassLoader cl = Knot.class.getClassLoader();

		if (embeddedGameProvider != null
				&& embeddedGameProvider.isEnabled()
				&& embeddedGameProvider.locateGame(envType, args, cl)) {
			return embeddedGameProvider;
		}

		// slow path with service loader

		List<GameProvider> failedProviders = new ArrayList<>();

		for (GameProvider provider : ServiceLoader.load(GameProvider.class)) {
			if (!provider.isEnabled()) continue; // don't attempt disabled providers and don't include them in the error report

			if (provider != embeddedGameProvider // don't retry already failed provider
					&& provider.locateGame(envType, args, cl)) {
				return provider;
			}

			failedProviders.add(provider);
		}

		// nothing found

		String msg;

		if (failedProviders.isEmpty()) {
			msg = "No game providers present on the class path!";
		} else if (failedProviders.size() == 1) {
			msg = String.format("%s game provider couldn't locate the game! "
					+ "The game may be absent from the class path, lacks some expected files, suffers from jar "
					+ "corruption or is of an unsupported variety/version.",
					failedProviders.get(0).getGameName());
		} else {
			msg = String.format("None of the game providers (%s) were able to locate their game!",
					failedProviders.stream().map(GameProvider::getGameName).collect(Collectors.joining(", ")));
		}

		Log.error(LogCategory.GAME_PROVIDER, msg);

		throw new RuntimeException(msg);
	}

	/**
	 * Find game provider embedded into the Fabric Loader jar, best effort.
	 *
	 * <p>This is faster than going through service loader because it only looks at a single jar.
	 */
	private static GameProvider findEmbedddedGameProvider() {
		try {
			Path flPath = UrlUtil.asPath(Knot.class.getProtectionDomain().getCodeSource().getLocation());
			if (!flPath.getFileName().toString().endsWith(".jar")) return null; // not a jar

			try (ZipFile zf = new ZipFile(flPath.toFile())) {
				ZipEntry entry = zf.getEntry("META-INF/services/net.fabricmc.loader.impl.game.GameProvider"); // same file as used by service loader
				if (entry == null) return null;

				try (InputStream is = zf.getInputStream(entry)) {
					byte[] buffer = new byte[100];
					int offset = 0;
					int len;

					while ((len = is.read(buffer, offset, buffer.length - offset)) >= 0) {
						offset += len;
						if (offset == buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
					}

					String content = new String(buffer, 0, offset, StandardCharsets.UTF_8).trim();
					if (content.indexOf('\n') >= 0) return null; // potentially more than one entry -> bail out

					int pos = content.indexOf('#');
					if (pos >= 0) content = content.substring(0, pos).trim();

					if (!content.isEmpty()) {
						return (GameProvider) Class.forName(content).getConstructor().newInstance();
					}
				}
			}

			return null;
		} catch (IOException | URISyntaxException | ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getTargetNamespace() {
		// TODO: Won't work outside of Yarn
		return isDevelopment ? "named" : "intermediary";
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		String cmdLineClasspath = System.getProperty("java.class.path");

		return Arrays.stream(cmdLineClasspath.split(File.pathSeparator)).filter((s) -> {
			if (s.equals("*") || s.endsWith(File.separator + "*")) {
				Log.warn(LogCategory.KNOT, "Knot does not support wildcard classpath entries: %s - the game may not load properly!", s);
				return false;
			} else {
				return true;
			}
		}).map((s) -> {
			File file = new File(s);

			if (!file.equals(gameJarFile)) {
				try {
					return (UrlUtil.asUrl(file));
				} catch (MalformedURLException e) {
					Log.debug(LogCategory.KNOT, "Can't determine url for %s", file, e);
					return null;
				}
			} else {
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	@Override
	public void addToClassPath(Path path) {
		Log.debug(LogCategory.KNOT, "Adding " + path + " to classpath.");

		try {
			classLoader.addURL(UrlUtil.asUrl(path));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return classLoader.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			return classLoader.getResourceAsStream(name, false);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file '" + name + "'!", e);
		}
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return (ClassLoader) classLoader;
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		if (runTransformers) {
			return classLoader.getDelegate().getPreMixinClassByteArray(name, false);
		} else {
			return classLoader.getDelegate().getRawClassByteArray(name, false);
		}
	}

	@Override
	public Manifest getManifest(Path originPath) {
		try {
			return classLoader.getDelegate().getMetadata(UrlUtil.asUrl(originPath)).manifest;
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	@Override
	public String getEntrypoint() {
		return provider.getEntrypoint();
	}

	public static void main(String[] args) {
		new Knot(null, null).init(args);
	}
}
