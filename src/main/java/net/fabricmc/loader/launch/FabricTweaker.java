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
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.Arguments;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;

public abstract class FabricTweaker extends FabricLauncherBase implements ITweaker {
	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Tweaker");
	protected Arguments arguments = new Arguments();
	private LaunchClassLoader launchClassLoader;
	private boolean isDevelopment;

	@Override
	public String getEntrypoint() {
		return getLaunchTarget();
	}

	@Override
	public void acceptOptions(List<String> localArgs, File gameDir, File assetsDir, String profile) {
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

		File gameDir = getLaunchDirectory(arguments);
		FabricLoader.INSTANCE.setGameDir(gameDir);
		FabricLoader.INSTANCE.load();
		FabricLoader.INSTANCE.freeze();

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

				FabricLauncherBase.deobfuscate(gameDir, jarFile, this);
			} catch (IOException | UrlConversionException e) {
				throw new RuntimeException(e);
			}
		}

		EntrypointTransformer.INSTANCE.locateEntrypoints(this);

		// Setup Mixin environment
		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), FabricLoader.INSTANCE);
		MixinEnvironment.getDefaultEnvironment().setSide(getEnvironmentType() == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
	}

	@Override
	public String[] getLaunchArguments() {
		return arguments.toArray();
	}

	@Override
	public void propose(URL url) {
		launchClassLoader.addURL(url);
	}

	@Override
	public void proposeJarClasspaths(File jarFile) {
		// TODO: read manifest and propose classpaths from jarFile.
	}

	@Override
	public Collection<URL> getClasspathURLs() {
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
	public byte[] getClassByteArray(String name) throws IOException {
		return launchClassLoader.getClassBytes(name);
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}
}