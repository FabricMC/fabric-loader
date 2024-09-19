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

package net.fabricmc.loader.impl.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class LoaderUtil {
	private static final ConcurrentMap<Path, Path> pathNormalizationCache = new ConcurrentHashMap<>();
	private static final String FABRIC_LOADER_CLASS = "net/fabricmc/loader/api/FabricLoader.class";
	private static final String ASM_CLASS = "org/objectweb/asm/ClassReader.class";

	public static String getClassFileName(String className) {
		return className.replace('.', '/').concat(".class");
	}

	public static Path normalizePath(Path path) {
		if (Files.exists(path)) {
			return normalizeExistingPath(path);
		} else {
			return path.toAbsolutePath().normalize();
		}
	}

	public static Path normalizeExistingPath(Path path) {
		return pathNormalizationCache.computeIfAbsent(path, LoaderUtil::normalizeExistingPath0);
	}

	private static Path normalizeExistingPath0(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void verifyNotInTargetCl(Class<?> cls) {
		if (cls.getClassLoader().getClass().getName().equals("net.fabricmc.loader.impl.launch.knot.KnotClassLoader")) {
			// This usually happens when fabric loader has been added to the target class loader. This is a bad state.
			// Such additions may be indirect, a JAR can use the Class-Path manifest attribute to drag additional
			// libraries with it, likely recursively.
			throw new IllegalStateException("trying to load "+cls.getName()+" from target class loader");
		}
	}

	public static void verifyClasspath() {
		try {
			List<URL> resources = Collections.list(LoaderUtil.class.getClassLoader().getResources(FABRIC_LOADER_CLASS));

			if (resources.size() > 1) {
				// This usually happens when fabric loader has been added to the classpath more than once.
				throw new IllegalStateException("duplicate fabric loader classes found on classpath: " + resources.stream().map(URL::toString).collect(Collectors.joining(", ")));
			} else if (resources.size() < 1) {
				throw new AssertionError(FABRIC_LOADER_CLASS + " not detected on the classpath?! (perhaps it was renamed?)");
			}

			resources = Collections.list(LoaderUtil.class.getClassLoader().getResources(ASM_CLASS));

			if (resources.size() > 1) {
				throw new IllegalStateException("duplicate ASM classes found on classpath: " + resources.stream().map(URL::toString).collect(Collectors.joining(", ")));
			} else if (resources.size() < 1) {
				throw new IllegalStateException("ASM not detected on the classpath (or perhaps " + ASM_CLASS + " was renamed?)");
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to get resources", e);
		}
	}

	public static boolean hasMacOs() {
		return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac");
	}

	public static boolean hasAwtSupport() {
		if (hasMacOs()) {
			// check for JAVA_STARTED_ON_FIRST_THREAD_<pid> which is set if -XstartOnFirstThread is used
			// -XstartOnFirstThread is incompatible with AWT (force enables embedded mode)
			for (String key : System.getenv().keySet()) {
				if (key.startsWith("JAVA_STARTED_ON_FIRST_THREAD_")) return false;
			}
		}

		return true;
	}
}
