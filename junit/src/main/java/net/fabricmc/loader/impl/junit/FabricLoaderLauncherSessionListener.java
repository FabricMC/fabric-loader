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

package net.fabricmc.loader.impl.junit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.SystemProperties;

public class FabricLoaderLauncherSessionListener implements LauncherSessionListener {
	private static final String PROPERTY_FILE_NAME = "fabric-loader-junit.properties";

	static {
		readClasspathSystemProperties();

		System.setProperty(SystemProperties.DEVELOPMENT, "true");
		System.setProperty(SystemProperties.UNIT_TEST, "true");
	}

	private final Knot knot;
	private final ClassLoader classLoader;

	private ClassLoader launcherSessionClassLoader;

	public FabricLoaderLauncherSessionListener() {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		final EnvType envType = EnvType.valueOf(System.getProperty(SystemProperties.UNIT_TEST_ENV, EnvType.CLIENT.name()).toUpperCase());

		try {
			knot = new Knot(envType);
			classLoader = knot.init(new String[]{});
		} finally {
			// Knot.init sets the context class loader, revert it back for now.
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		launcherSessionClassLoader = currentThread.getContextClassLoader();
		currentThread.setContextClassLoader(classLoader);
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		currentThread.setContextClassLoader(launcherSessionClassLoader);
	}

	// Read system properties from a "fabric-loader-junit.properties" file on the classpath.
	private static void readClasspathSystemProperties() {
		try (InputStream is = FabricLoaderLauncherSessionListener.class.getClassLoader().getResourceAsStream(PROPERTY_FILE_NAME)) {
			if (is == null) {
				return;
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line;

				while ((line = reader.readLine()) != null) {
					line = line.trim();

					if (line.isEmpty()) {
						continue;
					}

					final int pos = line.indexOf('=');

					if (pos == -1) {
						continue;
					}

					final String key = line.substring(0, pos).trim();
					final String value = line.substring(pos + 1).trim();
					System.setProperty(key, value);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load fabric loader junit properties", e);
		}
	}
}
