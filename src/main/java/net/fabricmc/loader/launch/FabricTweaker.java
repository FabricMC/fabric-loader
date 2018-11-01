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

import net.fabricmc.api.Side;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class FabricTweaker implements ITweaker {
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

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		File gameDir = new File(args.get("--gameDir"));
		mixinLoader = new MixinLoader();
		mixinLoader.load(new File(gameDir, "mods"));

		// Setup Mixin environment
		MixinBootstrap.init();
		FabricMixinBootstrap.init(getSide(), mixinLoader);
		MixinEnvironment.getDefaultEnvironment().setSide(getSide() == Side.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);

		if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
			// Development environment
			Launch.blackboard.put("fabric.development", true);

			// TODO: remove this
			launchClassLoader.registerTransformer("net.fabricmc.loader.transformer.PublicAccessTransformer");
		} else {
			// Obfuscated environment
			Launch.blackboard.put("fabric.development", false);
		}

		// Locate version-related files
		try {
			String target = getLaunchTarget();
			URL loc = launchClassLoader.findResource(target.replace('.', '/') + ".class");
			JarURLConnection locConn = (JarURLConnection) loc.openConnection();
			String jarFileName = locConn.getJarFileURL().getFile();
			File jarFile = new File(jarFileName);
			File mappingFile = new File(jarFileName.substring(0, jarFileName.lastIndexOf('.')) + ".tiny.gz");
			File deobfJarFile = new File(jarFileName.substring(0, jarFileName.lastIndexOf('.')) + "_remapped.jar");
			System.out.println(deobfJarFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String[] getLaunchArguments() {
		List<String> launchArgs = new ArrayList<>();
		for (Map.Entry<String, String> arg : this.args.entrySet()) {
			launchArgs.add(arg.getKey());
			launchArgs.add(arg.getValue());
 		}
		return launchArgs.toArray(new String[launchArgs.size()]);
	}

	public abstract Side getSide();
}
