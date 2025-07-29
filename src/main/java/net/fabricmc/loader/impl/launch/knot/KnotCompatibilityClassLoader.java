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

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.ClassLoaderAccess;

class KnotCompatibilityClassLoader extends URLClassLoader implements ClassLoaderAccess {
	private final KnotClassDelegate<KnotCompatibilityClassLoader> delegate;

	KnotCompatibilityClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
		super(new URL[0], KnotCompatibilityClassLoader.class.getClassLoader());
		this.delegate = new KnotClassDelegate<>(isDevelopment, envType, this, getParent(), provider);
	}

	KnotClassDelegate<?> getDelegate() {
		return delegate;
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
		super.addURL(url);
	}

	@Override
	public URL findResourceFwd(String name) {
		return findResource(name);
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

	static {
		registerAsParallelCapable();
	}
}
