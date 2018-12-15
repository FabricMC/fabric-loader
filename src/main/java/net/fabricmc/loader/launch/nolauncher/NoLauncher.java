package net.fabricmc.loader.launch.nolauncher;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.launch.common.CommonLauncherUtils;
import net.fabricmc.loader.launch.common.FabricLauncher;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.launch.common.MixinLoader;
import net.fabricmc.loader.transformer.PublicAccessTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class NoLauncher implements FabricLauncher {
	public static NoLauncher INSTANCE = new NoLauncher();
	protected static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");
	public Map<String, Object> properties = new HashMap<>();

	private PatchingClassLoader loader;
	private boolean isDevelopment;
	private EnvType envType;
	private String entryPoint;
	private List<URL> classpath;

	private static class NullClassLoader extends ClassLoader {
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
			return new Enumeration<URL>() {
				@Override
				public boolean hasMoreElements() {
					return false;
				}

				@Override
				public URL nextElement() {
					return null;
				}
			};
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
		private final List<String> excludedClasses = new ArrayList<>();
		private final DynamicURLClassLoader urlLoader;
		private final ClassLoader originalLoader;
		private MixinTransformer mixinTransformer;

		public PatchingClassLoader() {
			super(new DynamicURLClassLoader(new URL[0]));
			this.originalLoader = getClass().getClassLoader();
			this.urlLoader = (DynamicURLClassLoader) getParent();
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
					byte[] input = getClassByteArray(name, true);
					if (input != null) {
						if (name.indexOf('.') < 0) {
							throw new ClassNotFoundException("Root packages forbidden: class '" + name + "' could not be loaded");
						}

						if (mixinTransformer == null) {
							try {
								Constructor<MixinTransformer> constructor = MixinTransformer.class.getDeclaredConstructor();
								constructor.setAccessible(true);
								mixinTransformer = constructor.newInstance();
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}

						byte[] b = NoLauncher.INSTANCE.isDevelopment() ? PublicAccessTransformer.transform(name, input) : input;
						b = mixinTransformer.transformClassBytes(name, name, b);
						c = defineClass(name, b, 0, b.length);
					}
				}

				if (c == null) {
					c = originalLoader.loadClass(name);
				}

				if (c == null) {
					throw new ClassNotFoundException(name);
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

		public byte[] getClassByteArray(String name, boolean forTransforming) {
			String classFile = name.replace('.', '/') + ".class";
			try {
				InputStream inputStream = urlLoader.getResourceAsStream(classFile);
				if (inputStream == null && !forTransforming) {
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
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void init(String[] args) {
		CommonLauncherUtils.properties = properties;
		CommonLauncherUtils.setLauncher(this);

		String side = System.getProperty("fabric.side");
		if (side == null || side.isEmpty()) {
			side = "CLIENT";
		}

		side = side.toLowerCase();
		if ("client".equals(side)) {
			envType = EnvType.CLIENT;
		} else if ("server".equals(side)) {
			envType = EnvType.SERVER;
		} else {
			throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
		}

		isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));
		entryPoint = envType == EnvType.CLIENT ? "net.minecraft.client.main.Main" : "net.minecraft.server.MinecraftServer";

		// parse args
		Map<String, String> argMap = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("--")) {
				argMap.put(arg, args[++i]);
			}
		}

		CommonLauncherUtils.processArgumentMap(argMap, envType);
		String[] newArgs = CommonLauncherUtils.asStringArray(argMap);

		// Setup classloader

		loader = new PatchingClassLoader();
		String[] classpathStrings = System.getProperty("java.class.path").split(":");

		classpath = new ArrayList<>(classpathStrings.length - 1);
		populateClasspath(argMap, classpathStrings);

		// Setup Mixin environment
		MixinLoader mixinLoader = new MixinLoader();
		mixinLoader.load(new File(".", "mods"));
		mixinLoader.freeze();

		MixinBootstrap.init();
		if (isDevelopment) {
			CommonLauncherUtils.withMappingReader(
				(reader) -> FabricMixinBootstrap.init(envType, argMap, mixinLoader, reader),
				() -> FabricMixinBootstrap.init(envType, argMap, mixinLoader));
		} else {
			FabricMixinBootstrap.init(envType, argMap, mixinLoader);
		}
		MixinEnvironment.getDefaultEnvironment().setSide(envType == EnvType.CLIENT ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);

		try {
			Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
			m.setAccessible(true);
			m.invoke(null, MixinEnvironment.Phase.INIT);
			m.invoke(null, MixinEnvironment.Phase.DEFAULT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		try {
			Class c = loader.loadClass(entryPoint);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) newArgs);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void populateClasspath(Map<String, String> argMap, String[] classpathStrings) {
		String entryPointFilename = entryPoint.replace('.', '/') + ".class";
		File gameFile = null;

		for (String filename : classpathStrings) {
			File file = new File(filename);
			boolean hasGame = false;

			if (file.isDirectory()) {
				hasGame = new File(gameFile, entryPointFilename).exists();
			} else if (file.isFile()) {
				try {
					JarFile jf = new JarFile(file);
					ZipEntry entry = jf.getEntry(entryPointFilename);
					hasGame = entry != null;
				} catch (IOException e) {
					// pass
				}
			}

			if (hasGame) {
				if (gameFile != null && !gameFile.equals(file)) {
					throw new RuntimeException("Found duplicate game instances: [" + gameFile + ", " + file + "]");
				}
				gameFile = file;
			} else {
				try {
					URL url = file.toURI().toURL();
					classpath.add(url);
					System.out.println("adding " + url);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}

		if (gameFile == null) {
			throw new RuntimeException("Entrypoint '" + entryPoint + "' not found!");
		}

		CommonLauncherUtils.deobfuscate(
			new File(argMap.getOrDefault("--gameDir", ".")),
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
	public byte[] getClassByteArray(String name) {
		return loader.getClassByteArray(name, false);
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	public static void main(String[] args) {
		NoLauncher.INSTANCE.init(args);
	}
}
