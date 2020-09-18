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

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.entrypoint.minecraft.hooks.EntrypointUtils;
import net.fabricmc.loader.game.GameProvider;
import net.fabricmc.loader.game.MinecraftGameProvider;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.Proxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public abstract class FabricTweaker extends FabricLauncherBase implements ITweaker {
	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Tweaker");
	protected Arguments arguments;
	private LaunchClassLoader launchClassLoader;
	private boolean isDevelopment;

	@SuppressWarnings("unchecked")
	private final boolean isPrimaryTweaker = ((List<ITweaker>) Launch.blackboard.get("Tweaks")).isEmpty();

	@Override
	public String getEntrypoint() {
		return getLaunchTarget();
	}

	@Override
	public String getTargetNamespace() {
		// TODO: Won't work outside of Yarn
		return isDevelopment ? "named" : "intermediary";
	}

	@Override
	public void acceptOptions(List<String> localArgs, File gameDir, File assetsDir, String profile) {
		arguments = new Arguments();
		arguments.parse(localArgs);

		if (!arguments.containsKey("gameDir") && gameDir != null) {
			arguments.put("gameDir", gameDir.getAbsolutePath());
		}

		if (getEnvironmentType() == EnvType.CLIENT && !arguments.containsKey("assetsDir") && assetsDir != null) {
			arguments.put("assetsDir", assetsDir.getAbsolutePath());
		}

		FabricLauncherBase.processArgumentMap(arguments, getEnvironmentType());
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		Launch.blackboard.put("fabric.development", isDevelopment);
		setProperties(Launch.blackboard);

		this.launchClassLoader = launchClassLoader;
		launchClassLoader.addClassLoaderExclusion("org.objectweb.asm.");
		launchClassLoader.addClassLoaderExclusion("org.spongepowered.asm.");
		launchClassLoader.addClassLoaderExclusion("net.fabricmc.loader.");

		launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.Environment");
		launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.EnvType");
		launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.ModInitializer");
		launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.ClientModInitializer");
		launchClassLoader.addClassLoaderExclusion("net.fabricmc.api.DedicatedServerModInitializer");

		// FIXME: remove the GSON exclusion once loader stops using it (or repackages it)
		launchClassLoader.addClassLoaderExclusion("com.google.gson.");

		GameProvider provider = new MinecraftGameProvider();

		if (!provider.locateGame(getEnvironmentType(), launchClassLoader)) {
			throw new RuntimeException("Could not locate Minecraft: provider locate failed");
		}
		provider.acceptArguments(arguments.toArray());

		@SuppressWarnings("deprecation")
		FabricLoader loader = FabricLoader.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		loader.freeze();

		launchClassLoader.registerTransformer("net.fabricmc.loader.launch.FabricClassTransformer");

		if (!isDevelopment) {
			// Obfuscated environment
			Launch.blackboard.put("fabric.development", false);
			try {
				String target = getLaunchTarget();
				URL loc = launchClassLoader.findResource(target.replace('.', '/') + ".class");
				JarURLConnection locConn = (JarURLConnection) loc.openConnection();
				File jarFile = UrlUtil.asFile(locConn.getJarFileURL());
				if (!jarFile.exists()) {
					throw new RuntimeException("Could not locate Minecraft: " + jarFile.getAbsolutePath() + " not found");
				}

				Path obfuscated = jarFile.toPath();
				Path remapped = FabricLauncherBase.deobfuscate(provider.getGameId(), provider.getNormalizedGameVersion(), provider.getLaunchDirectory(), obfuscated, this);
				if (remapped != obfuscated) {
					preloadRemappedJar(remapped);
				}
			} catch (IOException | UrlConversionException e) {
				throw new RuntimeException("Failed to deobfuscate Minecraft!", e);
			}
		}

		FabricLoader.INSTANCE.getAccessWidener().loadFromMods();

		MinecraftGameProvider.TRANSFORMER.locateEntrypoints(this);

		// Setup Mixin environment
		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), FabricLoader.INSTANCE);
		MixinEnvironment.getDefaultEnvironment().setSide(getEnvironmentType() == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);

		EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
	}

	@Override
	public String[] getLaunchArguments() {
		return isPrimaryTweaker ? arguments.toArray() : new String[0];
	}

	@Override
	public void propose(URL url) {
		launchClassLoader.addURL(url);
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		return launchClassLoader.getSources();
	}

	@Override
	public boolean isClassLoaded(String name) {
		throw new RuntimeException("TODO isClassLoaded/launchwrapper");
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return launchClassLoader.getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return launchClassLoader;
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		String transformedName = name.replace('/', '.');
		byte[] classBytes = launchClassLoader.getClassBytes(name);

		if (runTransformers) {
			for (IClassTransformer transformer : launchClassLoader.getTransformers()) {
				if (transformer instanceof Proxy) {
					continue; // skip mixin as per method contract
				}
				classBytes = transformer.transform(name, transformedName, classBytes);
			}
		}

		return classBytes;
	}

	// By default the remapped jar will be on the classpath after the obfuscated one.
	// This will lead to us finding and the launching the obfuscated one when we search
	// for the entrypoint.
	// To work around that, we pre-popuplate the LaunchClassLoader's resource cache,
	// which will then cause it to use the one we need it to.
	private void preloadRemappedJar(Path remappedJarFile) throws IOException {
		Map<String, byte[]> resourceCache = null;
		try {
			Field f = LaunchClassLoader.class.getDeclaredField("resourceCache");
			f.setAccessible(true);
			//noinspection unchecked
			resourceCache = (Map<String, byte[]>) f.get(launchClassLoader);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (resourceCache == null) {
			LOGGER.warn("Resource cache not pre-populated - this will probably cause issues...");
			return;
		}

		try (FileInputStream jarFileStream = new FileInputStream(remappedJarFile.toFile());
			 JarInputStream jarStream = new JarInputStream(jarFileStream)) {
			JarEntry entry;

			while ((entry = jarStream.getNextJarEntry()) != null) {
				if (entry.getName().startsWith("net/minecraft/class_") || !entry.getName().endsWith(".class")) {
					// These will never be in the obfuscated jar, so we can safely skip them
					continue;
				}
				String className = entry.getName();
				className = className.substring(0, className.length() - 6).replace('/', '.');
				LOGGER.debug("Appending " + className + " to resource cache...");
				resourceCache.put(className, toByteArray(jarStream));
			}
		}
	}

	private byte[] toByteArray(InputStream inputStream) throws IOException {
		int estimate = inputStream.available();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(estimate < 32 ? 32768 : estimate);
		byte[] buffer = new byte[8192];
		int len;
		while ((len = inputStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, len);
		}

		return outputStream.toByteArray();
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}
}
