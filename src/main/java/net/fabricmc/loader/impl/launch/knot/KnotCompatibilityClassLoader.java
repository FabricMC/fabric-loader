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
import java.util.ArrayList;
import java.util.Collection;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;

class KnotCompatibilityClassLoader extends URLClassLoader implements KnotClassLoaderInterface {
	private final KnotClassDelegate delegate;
	private final Collection<URL> restrictedUrl;

	static {
		registerAsParallelCapable();
	}

	KnotCompatibilityClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
		super(new URL[0], KnotCompatibilityClassLoader.class.getClassLoader());
		this.delegate = new KnotClassDelegate(isDevelopment, envType, this, provider);
		this.restrictedUrl = new ArrayList<>();
	}

	@Override
	public KnotClassDelegate getDelegate() {
		return delegate;
	}

	@Override
	public boolean isClassLoaded(String name) {
		synchronized (getClassLoadingLock(name)) {
			return findLoadedClass(name) != null;
		}
	}

	@Override
	protected Class<?> findClass(final String name) throws ClassNotFoundException {
		byte[] input = delegate.getPostMixinClassByteArray(name);

		if (input == null) throw new ClassNotFoundException("[PostMixin]" + name);

		KnotClassDelegate.Metadata metadata = delegate.getMetadata(name, getResource(delegate.getClassFileName(name)));

		final Class<?> result = defineClass(name, input, 0, input.length, metadata.codeSource);

		if (result == null) throw new ClassNotFoundException(name);

		return result;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return super.loadClass(name, resolve);	// Please see findClass for more detail
	}

	@Override
	public void addURL(URL url) {
		super.addURL(url);
	}

	public void addRestrictedUrl(URL url) {
		this.restrictedUrl.add(url);
	}

	public void releaseRestriction() {
		for (URL url : restrictedUrl) {
			this.addURL(url);
		}

		restrictedUrl.clear();
	}

	@Override
	public InputStream getResourceAsStream(String classFile, boolean skipOriginalLoader) throws IOException {
		if (skipOriginalLoader && findResource(classFile) == null) {
			return null;
		}

		return super.getResourceAsStream(classFile);
	}
}
