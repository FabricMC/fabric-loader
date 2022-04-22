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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.ClassLoaderAccess;
import net.fabricmc.loader.impl.transformer.FabricTransformer;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

final class KnotClassDelegate<T extends ClassLoader & ClassLoaderAccess> implements KnotClassLoaderInterface {
	private static final boolean LOG_CLASS_LOAD_ERRORS = System.getProperty(SystemProperties.DEBUG_LOG_CLASS_LOAD_ERRORS) != null;
	private static final boolean LOG_TRANSFORM_ERRORS = System.getProperty(SystemProperties.DEBUG_LOG_TRANSFORM_ERRORS) != null;
	private static final boolean DISABLE_ISOLATION = System.getProperty(SystemProperties.DEBUG_DISABLE_CLASS_PATH_ISOLATION) != null;

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

	private final Map<URI, Metadata> metadataCache = new ConcurrentHashMap<>();
	private final T classLoader;
	private final ClassLoader parentClassLoader;
	private final GameProvider provider;
	private final boolean isDevelopment;
	private final EnvType envType;
	private IMixinTransformer mixinTransformer;
	private boolean transformInitialized = false;
	private volatile Set<URI> uris = Collections.emptySet();
	private volatile Set<URI> validParentUris = Collections.emptySet();
	private final Map<URI, String[]> allowedPrefixes = new ConcurrentHashMap<>();
	private final Set<String> parentSourcedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
	public void addUrl(URL url) {
		URI uri = UrlUtil.asUri(url);

		synchronized (this) {
			Set<URI> urls = this.uris;
			if (urls.contains(uri)) return;

			Set<URI> newUrls = new HashSet<>(urls.size() + 1, 1);
			newUrls.addAll(urls);
			newUrls.add(uri);

			this.uris = newUrls;
		}

		classLoader.addUrlFwd(url);
	}

	@Override
	public void setAllowedPrefixes(URL url, String... prefixes) {
		URI uri = UrlUtil.asUri(url);

		if (prefixes.length == 0) {
			allowedPrefixes.remove(uri);
		} else {
			allowedPrefixes.put(uri, prefixes);
		}
	}

	@Override
	public void setValidParentClassPath(Collection<URL> urls) {
		Set<URI> uris = new HashSet<>(urls.size(), 1);

		for (URL url : urls) {
			uris.add(UrlUtil.asUri(url));
		}

		this.validParentUris = uris;
	}

	@Override
	public Manifest getManifest(URL url) {
		return getMetadata(UrlUtil.asUri(url)).manifest;
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
							String msg = "can't find class "+name;
							if (LOG_CLASS_LOAD_ERRORS) Log.warn(LogCategory.KNOT, msg);
							throw new ClassNotFoundException(msg);
						} else if (!isValidParentUrl(url, fileName)) { // available, but restricted
							// The class would technically be available, but the game provider restricted it from being
							// loaded by setting validParentUrls and not including "url". Typical causes are:
							// - accessing classes too early (game libs shouldn't be used until Loader is ready)
							// - using jars that are only transient (deobfuscation input or pass-through installers)
							String msg = "can't load class "+name+" as it hasn't been exposed to the game (yet?)";
							if (LOG_CLASS_LOAD_ERRORS) Log.warn(LogCategory.KNOT, msg);
							throw new ClassNotFoundException(msg);
						} else { // load from system cl
							c = parentClassLoader.loadClass(name);
						}
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
	 * <p>This handles explicit parent url whitelisting by {@link #validParentUris} or shadowing by {@link #uris}
	 */
	private boolean isValidParentUrl(URL url, String fileName) {
		if (url == null) return false;
		if (DISABLE_ISOLATION) return true;

		try {
			URI srcUri = UrlUtil.getSource(fileName, url);
			Set<URI> validParentUris = this.validParentUris;

			if (validParentUris != null) { // explicit whitelist (in addition to platform cl classes)
				return validParentUris.contains(srcUri) || PLATFORM_CLASS_LOADER.getResource(fileName) != null;
			} else { // reject urls shadowed by this cl
				return !uris.contains(srcUri);
			}
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}
	}

	Class<?> tryLoadClass(String name, boolean allowFromParent) throws ClassNotFoundException {
		if (name.startsWith("java.")) {
			return null;
		}

		if (!allowedPrefixes.isEmpty() && !DISABLE_ISOLATION) { // check prefix restrictions (allows exposing libraries partially during startup)
			URL url = classLoader.getResource(LoaderUtil.getClassFileName(name));
			String[] prefixes;

			if (url != null
					&& (prefixes = allowedPrefixes.get(UrlUtil.asUri(url))) != null) {
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

		if (allowFromParent) {
			parentSourcedClasses.add(name);
		}

		KnotClassDelegate.Metadata metadata = getMetadata(name, classLoader.getResource(LoaderUtil.getClassFileName(name)));

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

	Metadata getMetadata(String name, URL resourceURL) {
		if (resourceURL == null) return Metadata.EMPTY;

		URI codeSource = null;

		try {
			codeSource = UrlUtil.getSource(LoaderUtil.getClassFileName(name), resourceURL);
		} catch (UrlConversionException e) {
			System.err.println("Could not find code source for " + resourceURL + ": " + e.getMessage());
		}

		if (codeSource == null) return Metadata.EMPTY;

		return getMetadata(codeSource);
	}

	Metadata getMetadata(URI codeSourceUri) {
		return metadataCache.computeIfAbsent(codeSourceUri, (URI uri) -> {
			Manifest manifest = null;
			CodeSource codeSource = null;
			Certificate[] certificates = null;

			try {
				Path path = UrlUtil.asPath(uri);

				if (Files.isDirectory(path)) {
					manifest = ManifestUtil.readManifest(path);
				} else {
					URLConnection connection = new URL("jar:" + uri.toString() + "!/").openConnection();

					if (connection instanceof JarURLConnection) {
						manifest = ((JarURLConnection) connection).getManifest();
						certificates = ((JarURLConnection) connection).getCertificates();
					}

					if (manifest == null) {
						try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(path, false)) {
							manifest = ManifestUtil.readManifest(jarFs.get().getRootDirectories().iterator().next());
						}
					}

					// TODO
					/* JarEntry codeEntry = codeSourceJar.getJarEntry(filename);

					if (codeEntry != null) {
						codeSource = new CodeSource(codeSourceURL, codeEntry.getCodeSigners());
					} */
				}
			} catch (IOException | FileSystemNotFoundException | URISyntaxException e) {
				if (FabricLauncherBase.getLauncher().isDevelopment()) {
					Log.warn(LogCategory.KNOT, "Failed to load manifest", e);
				}
			}

			if (codeSource == null) {
				codeSource = new CodeSource(UrlUtil.asUrl(uri), certificates);
			}

			return new Metadata(manifest, codeSource);
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
			if (!isValidParentUrl(url, name)) return null;
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
}
