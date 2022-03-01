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

package net.fabricmc.loader.impl.game.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import net.fabricmc.loader.impl.game.LibClassifier;
import net.fabricmc.loader.impl.util.LoaderUtil;

final class BundlerProcessor {
	private static final String MAIN_CLASS_PROPERTY = "bundlerMainClass";

	static void process(LibClassifier<McLibrary> classifier) throws IOException {
		Path bundlerOrigin = classifier.getOrigin(McLibrary.MC_BUNDLER);

		// determine urls by running the bundler and extracting them from the context class loader

		String prevProperty = null;
		ClassLoader prevCl = null;
		boolean restorePrev = false;
		URL[] urls;

		try (URLClassLoader bundlerCl = new URLClassLoader(new URL[] { bundlerOrigin.toUri().toURL() }, MinecraftGameProvider.class.getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					Class<?> c = findLoadedClass(name);

					if (c == null) {
						if (name.startsWith("net.minecraft.")) {
							URL url = getResource(LoaderUtil.getClassFileName(name));

							if (url != null) {
								try (InputStream is = url.openConnection().getInputStream()) {
									byte[] data = new byte[Math.max(is.available() + 1, 1000)];
									int offset = 0;
									int len;

									while ((len = is.read(data, offset, data.length - offset)) >= 0) {
										offset += len;
										if (offset == data.length) data = Arrays.copyOf(data, data.length * 2);
									}

									c = defineClass(name, data, 0, offset);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
						}

						if (c == null) {
							c = getParent().loadClass(name);
						}
					}

					if (resolve) {
						resolveClass(c);
					}

					return c;
				}
			}
		}) {
			Class<?> cls = Class.forName(classifier.getClassName(McLibrary.MC_BUNDLER), true, bundlerCl);
			Method method = cls.getMethod("main", String[].class);

			// save + restore the system property and context class loader just in case

			prevProperty = System.getProperty(MAIN_CLASS_PROPERTY);
			prevCl = Thread.currentThread().getContextClassLoader();
			restorePrev = true;

			System.setProperty(MAIN_CLASS_PROPERTY, BundlerClassPathCapture.class.getName());
			Thread.currentThread().setContextClassLoader(bundlerCl);

			method.invoke(null, (Object) new String[0]);
			urls = BundlerClassPathCapture.FUTURE.get(10, TimeUnit.SECONDS);
		} catch (ClassNotFoundException e) { // no bundler on the class path
			return;
		} catch (Throwable t) {
			throw new RuntimeException("Error invoking MC server bundler: "+t, t);
		} finally {
			if (restorePrev) {
				Thread.currentThread().setContextClassLoader(prevCl);

				if (prevProperty != null) {
					System.setProperty(MAIN_CLASS_PROPERTY, prevProperty);
				} else {
					System.clearProperty(MAIN_CLASS_PROPERTY);
				}
			}
		}

		// analyze urls to determine game/realms/log4j/misc libs and the entrypoint

		classifier.remove(bundlerOrigin);

		for (URL url : urls) {
			classifier.process(url);
		}
	}
}
