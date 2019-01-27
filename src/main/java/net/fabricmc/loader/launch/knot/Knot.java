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
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.launch.common.MixinLoader;
import net.fabricmc.loader.transformer.FabricTransformer;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.args.Arguments;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public final class Knot extends FabricLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private PatchingClassLoader loader;
	private boolean isDevelopment;
	private EnvType envType;
	private String entryPoint;
	private File gameJarFile;
	private List<URL> classpath;

	private static class NullClassLoader extends ClassLoader {
		private static final Enumeration<URL> NULL_ENUMERATION = new Enumeration<URL>() {
			@Override
			public boolean hasMoreElements() {
				return false;
			}

			@Override
			public URL nextElement() {
				return null;
			}
		};

		static {
			registerAsParallelCapable();
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			throw new ClassNotFoundException(name);
		}

		@Override
		public URL getResource(String name) {
			return null;
		}

		@Override
		public Enumeration<URL> getResources(String var1) throws IOException {
			return NULL_ENUMERATION;
		}
	}

	private static class DynamicURLClassLoader extends URLClassLoader {
		public DynamicURLClassLoader(URL[] urls) {
			super(urls, new NullClassLoader());
		}

		public void addURL(URL url) {
			super.addURL(url);
		}

		static {
			registerAsParallelCapable();
		}
	}

	private static class PatchingClassLoader extends ClassLoader {
		private final DynamicURLClassLoader urlLoader;
		private final ClassLoader originalLoader;
		private final boolean isDevelopment;
		private final EnvType envType;
		private MixinTransformer mixinTransformer;

		public PatchingClassLoader(boolean isDevelopment, EnvType envType) {
			super(new DynamicURLClassLoader(new URL[0]));
			this.originalLoader = getClass().getClassLoader();
			this.urlLoader = (DynamicURLClassLoader) getParent();
			this.isDevelopment = isDevelopment;
			this.envType = envType;
		}

		public boolean isClassLoaded(String name) {
			synchronized (getClassLoadingLock(name)) {
				return findLoadedClass(name) != null;
			}
		}

		@Override
		public URL getResource(String name) {
			Objects.requireNonNull(name);

			URL url = urlLoader.getResource(name);
			if (url == null) {
				url = originalLoader.getResource(name);
			}
			return url;
		}

		@Override
		public InputStream getResourceAsStream(String var1) {
			return super.getResourceAsStream(var1);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			Objects.requireNonNull(name);

			Enumeration<URL> first = urlLoader.getResources(name);
			Enumeration<URL> second = originalLoader.getResources(name);
			return new Enumeration<URL>() {
				Enumeration<URL> current = first;

				@Override
				public boolean hasMoreElements() {
					return current != null && current.hasMoreElements();
				}

				@Override
				public URL nextElement() {
					if (current == null) {
						return null;
					}

					if (!current.hasMoreElements()) {
						if (current == first) {
							current = second;
						} else {
							current = null;
							return null;
						}
					}

					return current.nextElement();
				}
			};
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				Class<?> c = findLoadedClass(name);

				if (c == null) {
					if (!"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && !name.startsWith("org.apache.logging.log4j")) {
						byte[] input = EntrypointTransformer.INSTANCE.transform(name);
						if (input == null) {
							try {
								input = getClassByteArray(name, true);
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						}

						if (input != null) {
							if (mixinTransformer == null) {
								try {
									Constructor<MixinTransformer> constructor = MixinTransformer.class.getDeclaredConstructor();
									constructor.setAccessible(true);
									mixinTransformer = constructor.newInstance();
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							}

							ProtectionDomain protectionDomain = null;
							URL resourceURL = urlLoader.getResource(getClassFileName(name));
							if (resourceURL != null) {
								URL codeSourceURL = null;

								try {
									URLConnection connection = resourceURL.openConnection();
									if (connection instanceof JarURLConnection) {
										codeSourceURL = ((JarURLConnection) connection).getJarFileURL();
									} else {
										// assume directory
										String s = UrlUtil.asFile(resourceURL).getAbsolutePath();
										s = s.replace(name.replace('.', File.separatorChar), "");
										codeSourceURL = UrlUtil.asUrl(new File(s));
									}
								} catch (Exception e) {
									e.printStackTrace();
								}

								// TODO: protection domain stub
								if (codeSourceURL != null) {
									protectionDomain = new ProtectionDomain(new CodeSource(codeSourceURL, (CodeSigner[]) null), null);
								}
							}

							byte[] b = FabricTransformer.transform(isDevelopment, envType, name, input);
							b = mixinTransformer.transformClassBytes(name, name, b);
							c = defineClass(name, b, 0, b.length, protectionDomain);

							int pkgDelimiterPos = name.lastIndexOf('.');
							if (pkgDelimiterPos > 0) {
								// TODO: package definition stub
								String pkgString = name.substring(0, pkgDelimiterPos);
								if (getPackage(pkgString) == null) {
									definePackage(pkgString, null, null, null, null, null, null, null);
								}
							}
						}
					}
				}

				if (c == null) {
					c = originalLoader.loadClass(name);
				}

				if (resolve) {
					resolveClass(c);
				}

				return c;
			}
        }

		public void addURL(URL url) {
			urlLoader.addURL(url);
		}

		static {
			registerAsParallelCapable();
		}

		private String getClassFileName(String name) {
			return name.replace('.', '/') + ".class";
		}

		public byte[] getClassByteArray(String name, boolean skipOriginalLoader) throws IOException {
			String classFile = getClassFileName(name);
			InputStream inputStream = urlLoader.getResourceAsStream(classFile);
			if (inputStream == null && !skipOriginalLoader) {
				inputStream = originalLoader.getResourceAsStream(classFile);
			}
			if (inputStream == null) {
				return null;
			}

			int a = inputStream.available();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(a < 32 ? 32768 : a);
			byte[] buffer = new byte[8192];
			int len;
			while ((len = inputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, len);
			}

			inputStream.close();
			return outputStream.toByteArray();
		}
	}

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
		String proposedEntrypoint = System.getProperty("fabric.entrypoint");

		// Setup classloader
		loader = new PatchingClassLoader(isDevelopment(), envType);
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

		Thread.currentThread().setContextClassLoader(loader);

		// Setup Mixin environment
		MixinLoader mixinLoader = new MixinLoader();
		mixinLoader.load(new File(getLaunchDirectory(arguments), "mods"));
		mixinLoader.freeze();

		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), mixinLoader);
		MixinEnvironment.getDefaultEnvironment().setSide(envType == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);

		FabricLauncherBase.pretendMixinPhases();

		try {
			Class<?> c = loader.loadClass(entryPoint);
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
		return loader.getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return loader;
	}

	@Override
	public byte[] getClassByteArray(String name) throws IOException {
		return loader.getClassByteArray(name, false);
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
