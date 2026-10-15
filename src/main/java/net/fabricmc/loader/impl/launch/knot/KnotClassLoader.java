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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.ClassLoaderAccess;
import net.fabricmc.loader.impl.mrj.AbstractSecureClassLoader;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

// class name referenced by string constant in net.fabricmc.loader.impl.util.LoaderUtil.verifyNotInTargetCl
final class KnotClassLoader extends AbstractSecureClassLoader implements ClassLoaderAccess {
	private static final class DynamicURLClassLoader extends URLClassLoader implements URLLoader {
		private DynamicURLClassLoader(URL[] urls) {
			super(urls, new DummyClassLoader());
		}

		@Override
		public void addURL(URL url) {
			super.addURL(url);
		}

		static {
			registerAsParallelCapable();
		}
	}

	private final URLLoader urlLoader;
	private final ClassLoader originalLoader;
	private final KnotClassDelegate<KnotClassLoader> delegate;

	KnotClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
		super("knot", obtainClassloader(provider));
		this.originalLoader = getClass().getClassLoader();
		this.urlLoader = (URLLoader) getParent();
		this.delegate = new KnotClassDelegate<>(isDevelopment, envType, this, originalLoader, provider);

		if (!(urlLoader instanceof DynamicURLClassLoader)) {
			Log.info(LogCategory.KNOT, "Found provider classloader: " + urlLoader.getClass().getCanonicalName());
		}
	}

	KnotClassDelegate<?> getDelegate() {
		return delegate;
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
	public URL findResource(String name) {
		Objects.requireNonNull(name);

		return urlLoader.findResource(name);
	}

	@Override
	public Enumeration<URL> findResources(String name) throws IOException {
		Objects.requireNonNull(name);

		return urlLoader.findResources(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		Objects.requireNonNull(name);

		InputStream inputStream = urlLoader.getResourceAsStream(name);

		if (inputStream == null) {
			inputStream = originalLoader.getResourceAsStream(name);
		}

		return inputStream;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		Objects.requireNonNull(name);

		return new MultiEnumeration<URL>(
			urlLoader.getResources(name),
			originalLoader.getResources(name)
		);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return delegate.loadClass(name, resolve);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return delegate.tryLoadClass(name, false);
	}

	@Override
	protected String findLibrary(String libname) {
		return delegate.findLibrary(libname);
	}

	@Override
	public void addUrlFwd(URL url) {
		urlLoader.addURL(url);
	}

	@Override
	public URL findResourceFwd(String name) {
		return urlLoader.findResource(name);
	}

	@Override
	public Package getPackageFwd(String name) {
		return super.getPackage(name);
	}

	@Override
	public Package definePackageFwd(String name, String specTitle, String specVersion, String specVendor,
			String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
	}

	@Override
	public Object getClassLoadingLockFwd(String name) {
		return super.getClassLoadingLock(name);
	}

	@Override
	public Class<?> findLoadedClassFwd(String name) {
		return super.findLoadedClass(name);
	}

	@Override
	public Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs) {
		return super.defineClass(name, b, off, len, cs);
	}

	@Override
	public void resolveClassFwd(Class<?> cls) {
		super.resolveClass(cls);
	}

	private static ClassLoader obtainClassloader(GameProvider provider) { //cannot use intersecting return type: https://bugs.openjdk.org/browse/JDK-8380420
		URLLoader ret = provider.getProviderCL();

		if (ret == null) {
			ret = new DynamicURLClassLoader(new URL[0]);
		}

		return (ClassLoader) ret;
	}

	static {
		registerAsParallelCapable();
	}

	private static final class MultiEnumeration<T> implements Enumeration<T> {
		private final Enumeration<T>[] enumerations;
		private int index = 0;

		@SafeVarargs
		MultiEnumeration(Enumeration<T>... enumerations) {
			this.enumerations = enumerations;
			advance();
		}

		private void advance() {
			while (index < enumerations.length && !enumerations[index].hasMoreElements()) {
				index++;
			}
		}

		@Override
		public boolean hasMoreElements() {
			return index < enumerations.length;
		}

		@Override
		public T nextElement() {
			if (!hasMoreElements()) {
				throw new NoSuchElementException();
			}

			T element = enumerations[index].nextElement();

			if (!enumerations[index].hasMoreElements()) {
				index++;
				advance();
			}

			return element;
		}
	}
}
