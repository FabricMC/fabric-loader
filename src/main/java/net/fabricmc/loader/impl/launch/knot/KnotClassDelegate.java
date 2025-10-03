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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.ClassLoaderAccess;
import net.fabricmc.loader.impl.transformer.FabricTransformer;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

final class KnotClassDelegate<T extends ClassLoader & ClassLoaderAccess> implements KnotClassLoaderInterface {
	private static final boolean LOG_CLASS_LOAD = SystemProperties.isSet(SystemProperties.DEBUG_LOG_CLASS_LOAD);
	private static final boolean LOG_CLASS_LOAD_ERRORS = LOG_CLASS_LOAD || SystemProperties.isSet(SystemProperties.DEBUG_LOG_CLASS_LOAD_ERRORS);
	private static final boolean LOG_TRANSFORM_ERRORS = SystemProperties.isSet(SystemProperties.DEBUG_LOG_TRANSFORM_ERRORS);
	private static final boolean DISABLE_ISOLATION = SystemProperties.isSet(SystemProperties.DEBUG_DISABLE_CLASS_PATH_ISOLATION);

	static final class Metadata {
		static final Metadata EMPTY = new Metadata(null, null);

		final Manifest manifest;
		final CodeSource codeSource;

		Metadata(Manifest manifest, CodeSource codeSource) {
			this.manifest = manifest;
			this.codeSource = codeSource;
		}
	}

	private static final ClassLoader PLATFORM_CLASS_LOADER = getPlatformClassLoader();

	private final Map<Path, Metadata> metadataCache = new ConcurrentHashMap<>();
	private final T classLoader;
	private final ClassLoader parentClassLoader;
	private final GameProvider provider;
	private final boolean isDevelopment;
	private final EnvType envType;
	private IMixinTransformer mixinTransformer;
	private boolean transformInitialized = false;
	private volatile Set<Path> codeSources = Collections.emptySet();
	private volatile Set<Path> validParentCodeSources = null; // null = disabled isolation, game provider has to set it to opt in
	private final Map<Path, String[]> allowedPrefixes = new ConcurrentHashMap<>();
	private final Set<String> parentSourcedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private static final Collection<Path> JVM_NATIVE_DIRS = computeJvmNativeDirs();
	private static final Map<String, String> PROCESSED_NATIVES = new HashMap<>();

	KnotClassDelegate(boolean isDevelopment, EnvType envType, T classLoader, ClassLoader parentClassLoader, GameProvider provider) {
		this.isDevelopment = isDevelopment;
		this.envType = envType;
		this.classLoader = classLoader;
		this.parentClassLoader = parentClassLoader;
		this.provider = provider;
	}

	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public void initializeTransformers() {
		if (transformInitialized) throw new IllegalStateException("Cannot initialize KnotClassDelegate twice!");

		mixinTransformer = MixinServiceKnot.getTransformer();

		if (mixinTransformer == null) {
			try { // reflective instantiation for older mixin versions
				@SuppressWarnings("unchecked")
				Constructor<IMixinTransformer> ctor = (Constructor<IMixinTransformer>) Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getConstructor();
				ctor.setAccessible(true);
				mixinTransformer = ctor.newInstance();
			} catch (ReflectiveOperationException e) {
				Log.debug(LogCategory.KNOT, "Can't create Mixin transformer through reflection (only applicable for 0.8-0.8.2): %s", e);

				// both lookups failed (not received through IMixinService.offer and not found through reflection)
				throw new IllegalStateException("mixin transformer unavailable?");
			}
		}

		transformInitialized = true;
	}

	private IMixinTransformer getMixinTransformer() {
		assert mixinTransformer != null;
		return mixinTransformer;
	}

	@Override
	public void addCodeSource(Path path) {
		path = LoaderUtil.normalizeExistingPath(path);

		synchronized (this) {
			Set<Path> codeSources = this.codeSources;
			if (codeSources.contains(path)) return;

			Set<Path> newCodeSources = new HashSet<>(codeSources.size() + 1, 1);
			newCodeSources.addAll(codeSources);
			newCodeSources.add(path);

			this.codeSources = newCodeSources;
		}

		try {
			classLoader.addUrlFwd(UrlUtil.asUrl(path));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		if (LOG_CLASS_LOAD_ERRORS) Log.info(LogCategory.KNOT, "added code source %s", path);
	}

	@Override
	public void setAllowedPrefixes(Path codeSource, String... prefixes) {
		codeSource = LoaderUtil.normalizeExistingPath(codeSource);

		if (prefixes.length == 0) {
			allowedPrefixes.remove(codeSource);
		} else {
			allowedPrefixes.put(codeSource, prefixes);
		}
	}

	@Override
	public void setValidParentClassPath(Collection<Path> paths) {
		Set<Path> validPaths = new HashSet<>(paths.size(), 1);

		for (Path path : paths) {
			validPaths.add(LoaderUtil.normalizeExistingPath(path));
		}

		this.validParentCodeSources = validPaths;
	}

	@Override
	public Manifest getManifest(Path codeSource) {
		return getMetadata(LoaderUtil.normalizeExistingPath(codeSource)).manifest;
	}

	@Override
	public boolean isClassLoaded(String name) {
		synchronized (classLoader.getClassLoadingLockFwd(name)) {
			return classLoader.findLoadedClassFwd(name) != null;
		}
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		synchronized (classLoader.getClassLoadingLockFwd(name)) {
			Class<?> c = classLoader.findLoadedClassFwd(name);

			if (c == null) {
				c = tryLoadClass(name, true);

				if (c == null) {
					throw new ClassNotFoundException("can't find class "+name);
				} else if (LOG_CLASS_LOAD) {
					Log.info(LogCategory.KNOT, "loaded class %s into target", name);
				}
			}

			classLoader.resolveClassFwd(c);

			return c;
		}
	}

	Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (classLoader.getClassLoadingLockFwd(name)) {
			Class<?> c = classLoader.findLoadedClassFwd(name);

			if (c == null) {
				if (name.startsWith("java.")) { // fast path for java.** (can only be loaded by the platform CL anyway)
					c = PLATFORM_CLASS_LOADER.loadClass(name);
				} else {
					c = tryLoadClass(name, false); // try local load

					if (c == null) { // not available locally, try system class loader
						String fileName = LoaderUtil.getClassFileName(name);
						URL url = parentClassLoader.getResource(fileName);

						if (url == null) { // no .class file
							try {
								c = PLATFORM_CLASS_LOADER.loadClass(name);
								if (LOG_CLASS_LOAD) Log.info(LogCategory.KNOT, "loaded resources-less class %s from platform class loader");
							} catch (ClassNotFoundException e) {
								if (LOG_CLASS_LOAD_ERRORS) Log.warn(LogCategory.KNOT, "can't find class %s", name);
								throw e;
							}
						} else if (!isValidParentUrl(url, fileName)) { // available, but restricted
							// The class would technically be available, but the game provider restricted it from being
							// loaded by setting validParentUrls and not including "url". Typical causes are:
							// - accessing classes too early (game libs shouldn't be used until Loader is ready)
							// - using jars that are only transient (deobfuscation input or pass-through installers)
							String msg = String.format("can't load class %s at %s as it hasn't been exposed to the game (yet? The system property "+SystemProperties.PATH_GROUPS+" may not be set correctly in-dev)",
									name, getCodeSource(url, fileName));
							if (LOG_CLASS_LOAD_ERRORS) Log.warn(LogCategory.KNOT, msg);
							throw new ClassNotFoundException(msg);
						} else { // load from system cl
							if (LOG_CLASS_LOAD) Log.info(LogCategory.KNOT, "loading class %s using the parent class loader", name);
							c = parentClassLoader.loadClass(name);
						}
					} else if (LOG_CLASS_LOAD) {
						Log.info(LogCategory.KNOT, "loaded class %s", name);
					}
				}
			}

			if (resolve) {
				classLoader.resolveClassFwd(c);
			}

			return c;
		}
	}

	/**
	 * Check if an url is loadable by the parent class loader.
	 *
	 * <p>This handles explicit parent url whitelisting by {@link #validParentCodeSources} or shadowing by {@link #codeSources}
	 */
	private boolean isValidParentUrl(URL url, String fileName) {
		if (url == null) return false;
		if (DISABLE_ISOLATION) return true;
		if (!hasRegularCodeSource(url)) return true;

		Path codeSource = getCodeSource(url, fileName);
		Set<Path> validParentCodeSources = this.validParentCodeSources;

		if (validParentCodeSources != null) { // explicit whitelist (in addition to platform cl classes)
			return validParentCodeSources.contains(codeSource) || PLATFORM_CLASS_LOADER.getResource(fileName) != null;
		} else { // reject urls shadowed by this cl
			return !codeSources.contains(codeSource);
		}
	}

	Class<?> tryLoadClass(String name, boolean allowFromParent) throws ClassNotFoundException {
		if (name.startsWith("java.")) {
			return null;
		}

		if (!allowedPrefixes.isEmpty() && !DISABLE_ISOLATION) { // check prefix restrictions (allows exposing libraries partially during startup)
			String fileName = LoaderUtil.getClassFileName(name);
			URL url = classLoader.getResource(fileName);

			if (url != null && hasRegularCodeSource(url)) {
				Path codeSource = getCodeSource(url, fileName);
				String[] prefixes = allowedPrefixes.get(codeSource);

				if (prefixes != null) {
					assert prefixes.length > 0;
					boolean found = false;

					for (String prefix : prefixes) {
						if (name.startsWith(prefix)) {
							found = true;
							break;
						}
					}

					if (!found) {
						String msg = "class "+name+" is currently restricted from being loaded";
						if (LOG_CLASS_LOAD_ERRORS) Log.warn(LogCategory.KNOT, msg);
						throw new ClassNotFoundException(msg);
					}
				}
			}
		}

		if (!allowFromParent && !parentSourcedClasses.isEmpty()) { // propagate loadIntoTarget behavior to its nested classes
			int pos = name.length();

			while ((pos = name.lastIndexOf('$', pos - 1)) > 0) {
				if (parentSourcedClasses.contains(name.substring(0, pos))) {
					allowFromParent = true;
					break;
				}
			}
		}

		byte[] input = getPostMixinClassByteArray(name, allowFromParent);
		if (input == null) return null;

		// The class we're currently loading could have been loaded already during Mixin initialization triggered by `getPostMixinClassByteArray`.
		// If this is the case, we want to return the instance that was already defined to avoid attempting a duplicate definition.
		Class<?> existingClass = classLoader.findLoadedClassFwd(name);

		if (existingClass != null) {
			return existingClass;
		}

		if (allowFromParent) {
			parentSourcedClasses.add(name);
		}

		KnotClassDelegate.Metadata metadata = getMetadata(name);

		int pkgDelimiterPos = name.lastIndexOf('.');

		if (pkgDelimiterPos > 0) {
			// TODO: package definition stub
			String pkgString = name.substring(0, pkgDelimiterPos);

			if (classLoader.getPackageFwd(pkgString) == null) {
				try {
					classLoader.definePackageFwd(pkgString, null, null, null, null, null, null, null);
				} catch (IllegalArgumentException e) { // presumably concurrent package definition
					if (classLoader.getPackageFwd(pkgString) == null) throw e; // still not defined?
				}
			}
		}

		return classLoader.defineClassFwd(name, input, 0, input.length, metadata.codeSource);
	}

	private Metadata getMetadata(String name) {
		String fileName = LoaderUtil.getClassFileName(name);
		URL url = classLoader.getResource(fileName);
		if (url == null || !hasRegularCodeSource(url)) return Metadata.EMPTY;

		return getMetadata(getCodeSource(url, fileName));
	}

	private Metadata getMetadata(Path codeSource) {
		return metadataCache.computeIfAbsent(codeSource, (Path path) -> {
			Manifest manifest = null;
			CodeSource cs = null;
			Certificate[] certificates = null;

			try {
				if (Files.isDirectory(path)) {
					manifest = ManifestUtil.readManifestFromBasePath(path);
				} else {
					URLConnection connection = new URL("jar:" + path.toUri().toString() + "!/").openConnection();

					if (connection instanceof JarURLConnection) {
						manifest = ((JarURLConnection) connection).getManifest();
						certificates = ((JarURLConnection) connection).getCertificates();
					}

					if (manifest == null) {
						try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(path, false)) {
							manifest = ManifestUtil.readManifestFromBasePath(jarFs.get().getRootDirectories().iterator().next());
						}
					}

					// TODO
					/* JarEntry codeEntry = codeSourceJar.getJarEntry(filename);

					if (codeEntry != null) {
						cs = new CodeSource(codeSourceURL, codeEntry.getCodeSigners());
					} */
				}
			} catch (IOException | FileSystemNotFoundException e) {
				if (FabricLauncherBase.getLauncher().isDevelopment()) {
					Log.warn(LogCategory.KNOT, "Failed to load manifest", e);
				}
			}

			if (cs == null) {
				try {
					cs = new CodeSource(UrlUtil.asUrl(path), certificates);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}

			return new Metadata(manifest, cs);
		});
	}

	private byte[] getPostMixinClassByteArray(String name, boolean allowFromParent) {
		byte[] transformedClassArray = getPreMixinClassByteArray(name, allowFromParent);

		if (!transformInitialized || !canTransformClass(name)) {
			return transformedClassArray;
		}

		try {
			return getMixinTransformer().transformClassBytes(name, name, transformedClassArray);
		} catch (Throwable t) {
			String msg = String.format("Mixin transformation of %s failed", name);
			if (LOG_TRANSFORM_ERRORS) Log.warn(LogCategory.KNOT, msg, t);

			throw new RuntimeException(msg, t);
		}
	}

	@Override
	public byte[] getPreMixinClassBytes(String name) {
		return getPreMixinClassByteArray(name, true);
	}

	/**
	 * Runs all the class transformers except mixin.
	 */
	private byte[] getPreMixinClassByteArray(String name, boolean allowFromParent) {
		// some of the transformers rely on dot notation
		name = name.replace('/', '.');

		if (!transformInitialized || !canTransformClass(name)) {
			try {
				return getRawClassByteArray(name, allowFromParent);
			} catch (IOException e) {
				throw new RuntimeException("Failed to load class file for '" + name + "'!", e);
			}
		}

		byte[] input = provider.getEntrypointTransformer().transform(name);

		if (input == null) {
			try {
				input = getRawClassByteArray(name, allowFromParent);
			} catch (IOException e) {
				throw new RuntimeException("Failed to load class file for '" + name + "'!", e);
			}
		}

		if (input != null) {
			return FabricTransformer.transform(isDevelopment, envType, name, input);
		}

		return null;
	}

	private static boolean canTransformClass(String name) {
		name = name.replace('/', '.');
		// Blocking Fabric Loader classes is no longer necessary here as they don't exist on the modding class loader
		return /* !"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && */ !name.startsWith("org.apache.logging.log4j");
	}

	@Override
	public byte[] getRawClassBytes(String name) throws IOException {
		return getRawClassByteArray(name, true);
	}

	private byte[] getRawClassByteArray(String name, boolean allowFromParent) throws IOException {
		name = LoaderUtil.getClassFileName(name);
		URL url = classLoader.findResourceFwd(name);

		if (url == null) {
			if (!allowFromParent) return null;

			url = parentClassLoader.getResource(name);

			if (!isValidParentUrl(url, name)) {
				if (LOG_CLASS_LOAD) Log.info(LogCategory.KNOT, "refusing to load class %s at %s from parent class loader", name, getCodeSource(url, name));

				return null;
			}
		}

		try (InputStream inputStream = url.openStream()) {
			int a = inputStream.available();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(a < 32 ? 32768 : a);
			byte[] buffer = new byte[8192];
			int len;

			while ((len = inputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, len);
			}

			return outputStream.toByteArray();
		}
	}

	private static boolean hasRegularCodeSource(URL url) {
		return url.getProtocol().equals("file") || url.getProtocol().equals("jar");
	}

	private static Path getCodeSource(URL url, String fileName) {
		try {
			return LoaderUtil.normalizeExistingPath(UrlUtil.getCodeSource(url, fileName));
		} catch (UrlConversionException e) {
			throw ExceptionUtil.wrap(e);
		}
	}

	private static ClassLoader getPlatformClassLoader() {
		try {
			return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null); // Java 9+ only
		} catch (NoSuchMethodException e) {
			return new ClassLoader(null) { }; // fall back to boot cl
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	interface ClassLoaderAccess {
		void addUrlFwd(URL url);
		URL findResourceFwd(String name);

		Package getPackageFwd(String name);
		Package definePackageFwd(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException;

		Object getClassLoadingLockFwd(String name);
		Class<?> findLoadedClassFwd(String name);
		Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs);
		void resolveClassFwd(Class<?> cls);
	}

	private static Collection<Path> computeJvmNativeDirs() {
		Set<Path> ret = new HashSet<>();
		String[] libPathProperties = { "sun.boot.library.path", "java.library.path" };

		for (String libPathProperty : libPathProperties) {
			String value = System.getProperty(libPathProperty);
			if (value == null || value.isEmpty()) continue;

			for (String pathStr : value.split(File.pathSeparator)) {
				try {
					Path path = Paths.get(pathStr);

					if (Files.exists(path)) {
						ret.add(path);
					}
				} catch (InvalidPathException e) {
					Log.warn(LogCategory.KNOT, "Ignoring invalid library path %s", pathStr);
				}
			}
		}

		return ret;
	}

	/**
	 * Implementation of {@link ClassLoader#findLibrary} that falls back to grabbing natives from the class path.
	 *
	 * @param libname Library name to find
	 * @return Library path if available, null otherwise
	 */
	synchronized String findLibrary(String libname) {
		String ret = PROCESSED_NATIVES.get(libname); // cache for repeat queries, avoids expensive validation
		if (ret != null) return ret;

		String fileName = System.mapLibraryName(libname);

		// check if the jvm will provide the native after us

		for (Path dir : JVM_NATIVE_DIRS) {
			Path file = dir.resolve(fileName);

			if (Files.exists(file)) return null;
		}

		// check if we can find the native on the knot class path

		URL url = classLoader.getResource(fileName);
		if (url == null) return null;

		// grab native from class path, extracting it as needed

		Path codeSource;

		try {
			codeSource = UrlUtil.getCodeSource(url, fileName);
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}

		Path libFile;

		if (Files.isDirectory(codeSource)) { // cp entry is a folder, use library file directly
			libFile = codeSource.resolve(fileName);
		} else { // cp entry is a jar, extract library file
			Path cacheDir = null;

			try {
				cacheDir = FabricLoaderImpl.INSTANCE.getGameDir().resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("natives");
				assert cacheDir.isAbsolute();
				Files.createDirectories(cacheDir);
			} catch (IllegalStateException e) { // too early access
				return null;
			} catch (IOException e) {
				Log.warn(LogCategory.KNOT, "Error creating natives cache directory %s", cacheDir, e);
				return null;
			}

			libFile = cacheDir.resolve(fileName);
			Log.debug(LogCategory.KNOT, "Extracting native %s from class path %s to %s", libname, url, libFile);

			try {
				copyZipEntryIfDistinct(codeSource, fileName, libFile);
			} catch (IOException e) {
				Log.warn(LogCategory.KNOT, "Error extracting native %s to %s", url, cacheDir, e);
				return null;
			}
		}

		ret = libFile.toString();
		PROCESSED_NATIVES.put(libname, ret);

		Log.debug(LogCategory.KNOT, "Supplying native %s from class path (%s)", libname, ret);

		return ret;
	}

	private static void copyZipEntryIfDistinct(Path zipFile, String fileName, Path output) throws IOException {
		try (ZipFile zf = new ZipFile(zipFile.toFile())) {
			ZipEntry entry = zf.getEntry(fileName);
			if (entry == null) throw new FileNotFoundException(String.format("zip file %s doesn't contain %s", zipFile, fileName));

			if (Files.exists(output)) { // extracted file exists, check size and crc
				long expectedSize = entry.getSize();
				long expectedCrc = entry.getCrc();

				if (Files.size(output) == expectedSize) {
					CRC32 crc = new CRC32();
					byte[] buffer = new byte[0x4000];

					try (InputStream is = Files.newInputStream(output)) {
						int len;

						while ((len = is.read(buffer)) >= 0) {
							crc.update(buffer, 0, len);
						}
					}

					if (crc.getValue() == expectedCrc) { // existing file has the same size and crc as the zip entry
						return;
					}
				}
			}

			Files.copy(zf.getInputStream(entry), output, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
