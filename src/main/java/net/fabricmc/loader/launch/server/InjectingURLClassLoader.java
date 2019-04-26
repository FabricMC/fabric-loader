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

package net.fabricmc.loader.launch.server;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

class InjectingURLClassLoader extends URLClassLoader {
	private final List<String> exclusions;

	InjectingURLClassLoader(URL[] urls, ClassLoader classLoader, String... exclusions) {
		super(urls, classLoader);
		this.exclusions  = Arrays.asList(exclusions);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class c = findLoadedClass(name);

			if (c == null) {
				boolean excluded = false;
				for (String s : exclusions) {
					if (name.startsWith(s)) {
						excluded = true;
						break;
					}
				}

				if (!excluded) {
					try {
						c = findClass(name);
					} catch (ClassNotFoundException e) {
						// pass
					}
				}
			}

			if (c == null) {
				c = getParent().loadClass(name);
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
}
