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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.util.UrlUtil;

final class LibClassifier {
	private final Map<Lib, Path> matches = new EnumMap<>(Lib.class);
	private final Map<Lib, String> localPaths = new EnumMap<>(Lib.class);
	private final List<Path> unmatchedOrigins = new ArrayList<>();

	public void process(URL url, EnvType env) throws IOException {
		try {
			process(UrlUtil.asPath(url), env);
		} catch (URISyntaxException e) {
			throw new RuntimeException("invalid url: "+url);
		}
	}

	public void process(Iterable<Path> paths, EnvType env) throws IOException {
		for (Path path : paths) {
			process(path, env);
		}
	}

	public void process(Path path, EnvType env) throws IOException {
		boolean matched = false;

		if (Files.isDirectory(path)) {
			for (Lib lib : Lib.VALUES) {
				if (!lib.isInEnv(env) || matches.containsKey(lib)) continue;

				for (String p : lib.paths) {
					if (Files.exists(path.resolve(p))) {
						matched = true;
						matches.put(lib, path);
						localPaths.put(lib, p);
						break;
					}
				}
			}
		} else {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				for (Lib lib : Lib.VALUES) {
					if (!lib.isInEnv(env) || matches.containsKey(lib)) continue;

					for (String p : lib.paths) {
						ZipEntry entry = zf.getEntry(p);

						if (entry != null) {
							matched = true;
							matches.put(lib, path);
							localPaths.put(lib, p);
							break;
						}
					}
				}
			} catch (ZipError | IOException e) {
				throw new IOException("error reading "+path.toAbsolutePath(), e);
			}
		}

		if (!matched) unmatchedOrigins.add(path);
	}

	public boolean is(Path path, Lib... libs) {
		for (Lib lib : libs) {
			if (path.equals(matches.get(lib))) return true;
		}

		return false;
	}

	public boolean has(Lib lib) {
		return matches.containsKey(lib);
	}

	public Path getOrigin(Lib lib) {
		return matches.get(lib);
	}

	public String getLocalPath(Lib lib) {
		return localPaths.get(lib);
	}

	public String getClassName(Lib lib) {
		String localPath = localPaths.get(lib);
		if (localPath == null || !localPath.endsWith(".class")) return null;

		return localPath.substring(0, localPath.length() - 6).replace('/', '.');
	}

	public List<Path> getUnmatchedOrigins() {
		return unmatchedOrigins;
	}

	public boolean remove(Path path) {
		if (unmatchedOrigins.remove(path)) return true;

		boolean ret = false;

		for (Iterator<Map.Entry<Lib, Path>> it = matches.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Lib, Path> entry = it.next();

			if (entry.getValue().equals(path)) {
				localPaths.remove(entry.getKey());
				it.remove();

				ret = true;
			}
		}

		return ret;
	}

	enum Lib {
		MC_CLIENT(EnvType.CLIENT, "net/minecraft/client/main/Main.class", "net/minecraft/client/MinecraftApplet.class", "com/mojang/minecraft/MinecraftApplet.class"),
		MC_SERVER(EnvType.SERVER, "net/minecraft/server/Main.class", "net/minecraft/server/MinecraftServer.class", "com/mojang/minecraft/server/MinecraftServer.class"),
		MC_BUNDLER(EnvType.SERVER, "net/minecraft/bundler/Main.class"),
		REALMS(EnvType.CLIENT, "realmsVersion"),
		MODLOADER("ModLoader"),
		LOG4J_API("org/apache/logging/log4j/LogManager.class"),
		LOG4J_CORE("META-INF/services/org.apache.logging.log4j.spi.Provider", "META-INF/log4j-provider.properties"),
		LOG4J_CONFIG("log4j2.xml"),
		LOG4J_PLUGIN("com/mojang/util/QueueLogAppender.class"),
		SLF4J_API("org/slf4j/Logger.class"),
		SLF4J_CORE("META-INF/services/org.slf4j.spi.SLF4JServiceProvider");

		static final Lib[] VALUES = values();
		static final Lib[] LOGGING = { LOG4J_API, LOG4J_CORE, LOG4J_CONFIG, LOG4J_PLUGIN, SLF4J_API, SLF4J_CORE };

		final String[] paths;
		final EnvType env;

		Lib(String path) {
			this(null, new String[] { path });
		}

		Lib(String... paths) {
			this(null, paths);
		}

		Lib(EnvType env, String... paths) {
			this.paths = paths;
			this.env = env;
		}

		boolean isInEnv(EnvType env) {
			return this.env == null || this.env == env;
		}
	}
}
