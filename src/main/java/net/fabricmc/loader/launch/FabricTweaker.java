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
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public abstract class FabricTweaker extends FabricLauncherBase implements ITweaker {
	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Tweaker");
	protected Map<String, String> args;
	private LaunchClassLoader launchClassLoader;
	private boolean isDevelopment;

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

		if (!this.args.containsKey("--gameDir") && gameDir != null) {
			this.args.put("--gameDir", gameDir.getAbsolutePath());
		}

		if (getEnvironmentType() == EnvType.CLIENT && !this.args.containsKey("--assetsDir") && assetsDir != null) {
			this.args.put("--assetsDir", assetsDir.getAbsolutePath());
		}

		FabricLauncherBase.processArgumentMap(args, getEnvironmentType());
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		Launch.blackboard.put("fabric.development", isDevelopment);
		setProperties(Launch.blackboard);

		this.launchClassLoader = launchClassLoader;
		launchClassLoader.addClassLoaderExclusion("org.objectweb.asm.");
		launchClassLoader.addClassLoaderExclusion("org.spongepowered.asm.");

		File gameDir = getLaunchDirectory(this.args);
		FabricLoader.INSTANCE.load(new File(gameDir, "mods"));
		FabricLoader.INSTANCE.freeze();

		launchClassLoader.registerTransformer("net.fabricmc.loader.launch.FabricClassTransformer");

		if (!isDevelopment) {
			// Obfuscated environment
			Launch.blackboard.put("fabric.development", false);
			try {
				String target = getLaunchTarget();
				URL loc = launchClassLoader.findResource(target.replace('.', '/') + ".class");
				JarURLConnection locConn = (JarURLConnection) loc.openConnection();
				File jarFile = new File(locConn.getJarFileURL().toURI());
				if (!jarFile.exists()) {
					throw new RuntimeException("Could not locate Minecraft: " + jarFile.getAbsolutePath() + " not found");
				}

				FabricLauncherBase.deobfuscate(gameDir, jarFile, this);
			} catch (IOException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		// Setup Mixin environment
		MixinBootstrap.init();
		if (isDevelopment) {
			FabricLauncherBase.withMappingReader(
				(reader) -> FabricMixinBootstrap.init(getEnvironmentType(), args, FabricLoader.INSTANCE, reader),
				() -> FabricMixinBootstrap.init(getEnvironmentType(), args, FabricLoader.INSTANCE));
		} else {
			FabricMixinBootstrap.init(getEnvironmentType(), args, FabricLoader.INSTANCE);
		}
		MixinEnvironment.getDefaultEnvironment().setSide(getEnvironmentType() == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
	}

	@Override
	public String[] getLaunchArguments() {
		return FabricLauncherBase.asStringArray(args);
	}

	@Override
	public void propose(URL url) {
		launchClassLoader.addURL(url);
	}

	@Override
	public Collection<URL> getClasspathURLs() {
		return launchClassLoader.getSources();
	}

	public abstract EnvType getEnvironmentType();

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