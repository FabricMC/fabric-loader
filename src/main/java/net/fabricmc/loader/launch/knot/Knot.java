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

package net.fabricmc.loader.launch.knot;

import com.google.common.collect.Lists;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.Arguments;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public final class Knot extends FabricLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private KnotClassLoaderInterface loader;
	private boolean isDevelopment;
	private EnvType envType;
	private String entryPoint;
	private File gameJarFile;
	private List<URL> classpath;

	protected Knot(EnvType type, File gameJarFile) {
		this.envType = type;
		this.gameJarFile = gameJarFile;
	}

	protected void init(String[] args) {
		setProperties(properties);

		// parse args
		Arguments arguments = new Arguments();
		arguments.parse(args);

		// configure fabric vars
		if (envType == null) {
			String side = System.getProperty("fabric.side");
			if (side == null) {
				throw new RuntimeException("Please specify side or use a dedicated Knot!");
			}

			side = side.toLowerCase();
			if ("client".equals(side)) {
				envType = EnvType.CLIENT;
			} else if ("server".equals(side)) {
				envType = EnvType.SERVER;
			} else {
				throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
			}
		}

		FabricLauncherBase.processArgumentMap(arguments, envType);
		String[] newArgs = arguments.toArray();

		isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		String proposedEntrypoint = System.getProperty("fabric.loader.entrypoint");

		// Setup classloader
		// TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
		boolean useCompatibility = Boolean.parseBoolean(System.getProperty("fabric.loader.useCompatibilityClassLoader", "false"));
		loader = useCompatibility ? new KnotCompatibilityClassLoader(isDevelopment(), envType) : new KnotClassLoader(isDevelopment(), envType);

		String[] classpathStringsIn = System.getProperty("java.class.path").split(File.pathSeparator);
		List<String> classpathStrings = new ArrayList<>(classpathStringsIn.length);

		for (String s : classpathStringsIn) {
			if (s.equals("*") || s.endsWith(File.separator + "*")) {
				System.err.println("WARNING: Knot does not support wildcard classpath entries: " + s + " - the game may not load properly!");
			} else {
				classpathStrings.add(s);
			}
		}

		classpath = new ArrayList<>(classpathStrings.size() - 1);
		populateClasspath(arguments, classpathStrings,
			/* order by most to least important */
			proposedEntrypoint != null ? Collections.singletonList(proposedEntrypoint)
			: (envType == EnvType.CLIENT
			? Lists.newArrayList("net.minecraft.client.main.Main", "net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet")
			: Lists.newArrayList("net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer")));

		// Locate entrypoints before switching class loaders
		EntrypointTransformer.INSTANCE.locateEntrypoints(this);

		if (envType == EnvType.CLIENT && entryPoint.contains("Applet")) {
			entryPoint = "net.fabricmc.loader.entrypoint.applet.AppletMain";
		}

		Thread.currentThread().setContextClassLoader((ClassLoader) loader);

		FabricLoader.INSTANCE.setGameDir(new File("."));
		FabricLoader.INSTANCE.load();
		FabricLoader.INSTANCE.freeze();

		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), FabricLoader.INSTANCE);
		MixinEnvironment.getDefaultEnvironment().setSide(envType == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);

		FabricLauncherBase.pretendMixinPhases();

		try {
			Class<?> c = ((ClassLoader) loader).loadClass(entryPoint);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) newArgs);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private int findEntrypoint(List<String> entrypointClasses, List<String> entrypointFilenames, File file) {
		for (int i = 0; i < entrypointClasses.size(); i++) {
			String entryPointFilename = entrypointFilenames.get(i);

			if (file.isDirectory()) {
				if (new File(file, entryPointFilename).exists()) {
					return i;
				}
			} else if (file.isFile()) {
				try {
					JarFile jf = new JarFile(file);
					ZipEntry entry = jf.getEntry(entryPointFilename);
					if (entry != null) {
						return i;
					}
				} catch (IOException e) {
					// pass
				}
			}
		}

		return -1;
	}

	private void populateClasspath(Arguments argMap, Collection<String> classpathStrings, List<String> entrypointClasses) {
		List<String> entrypointFilenames = entrypointClasses.stream()
			.map((ep) -> ep.replace('.', '/') + ".class")
			.collect(Collectors.toList());
		File gameFile = this.gameJarFile;

		if (gameFile == null) {
			for (String filename : classpathStrings) {
				File file = new File(filename);
				int i = findEntrypoint(entrypointClasses, entrypointFilenames, file);

				if (i >= 0) {
					if (gameFile != null && !gameFile.equals(file)) {
						throw new RuntimeException("Found duplicate game instances: [" + gameFile + ", " + file + "]");
					}

					entryPoint = entrypointClasses.get(i);
					gameFile = file;
				} else {
					try {
						classpath.add(UrlUtil.asUrl(file));
					} catch (UrlConversionException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			for (String filename : classpathStrings) {
				File file = new File(filename);
				if (!file.equals(gameFile)) {
					try {
						classpath.add(UrlUtil.asUrl(file));
					} catch (UrlConversionException e) {
						e.printStackTrace();
					}
				}
			}

			int i = findEntrypoint(entrypointClasses, entrypointFilenames, gameFile);

			if (i >= 0) {
				entryPoint = entrypointClasses.get(i);
			}
		}

		if (entryPoint == null) {
			throw new RuntimeException("Entrypoint not found!");
		}

		FabricLauncherBase.deobfuscate(
			getLaunchDirectory(argMap),
			gameFile,
			this
		);
	}

	@Override
	public void propose(URL url) {
		loader.addURL(url);
	}

	@Override
	public void proposeJarClasspaths(File jarFile) {
		try (JarFile jf = new JarFile(jarFile)) {
			ZipEntry manifestEntry = jf.getEntry("META-INF/MANIFEST.MF");
			if (manifestEntry != null) {
				Manifest manifest = new Manifest(jf.getInputStream(manifestEntry));
				for(String cp : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH).split(" ")) {
					try {
						propose(UrlUtil.asUrl(new File(cp)));
					} catch (UrlConversionException e) {
						e.printStackTrace();
					}
				}
			}
		}catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public Collection<URL> getClasspathURLs() {
		return Collections.unmodifiableList(classpath);
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return loader.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			return loader.getResourceAsStream(name, false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return (ClassLoader) loader;
	}

	@Override
	public byte[] getClassByteArray(String name) throws IOException {
		return loader.getDelegate().getClassByteArray(name, false);
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	@Override
	public String getEntrypoint() {
		return entryPoint;
	}

	public static void main(String[] args) {
		new Knot(null, null).init(args);
	}
}
