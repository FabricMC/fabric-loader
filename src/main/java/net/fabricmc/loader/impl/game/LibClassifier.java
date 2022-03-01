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

package net.fabricmc.loader.impl.game;

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
import net.fabricmc.loader.impl.game.LibClassifier.LibraryType;
import net.fabricmc.loader.impl.util.UrlUtil;

public final class LibClassifier<L extends Enum<L> & LibraryType> {
	private final L[] libs;
	private final Map<L, Path> origins;
	private final Map<L, String> localPaths;
	private final List<Path> unmatchedOrigins = new ArrayList<>();

	public LibClassifier(Class<L> cls) {
		libs = cls.getEnumConstants();
		origins = new EnumMap<>(cls);
		localPaths = new EnumMap<>(cls);
	}

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
			for (L lib : libs) {
				if (!lib.isInEnv(env) || origins.containsKey(lib)) continue;

				for (String p : lib.getPaths()) {
					if (Files.exists(path.resolve(p))) {
						matched = true;
						addLibrary(lib, path, p);
						break;
					}
				}
			}
		} else {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				for (L lib : libs) {
					if (!lib.isInEnv(env) || origins.containsKey(lib)) continue;

					for (String p : lib.getPaths()) {
						ZipEntry entry = zf.getEntry(p);

						if (entry != null) {
							matched = true;
							addLibrary(lib, path, p);
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

	private void addLibrary(L lib, Path originPath, String localPath) {
		origins.put(lib, originPath);
		localPaths.put(lib, localPath);
	}

	@SafeVarargs
	public final boolean is(Path path, L... libs) {
		for (L lib : libs) {
			if (path.equals(origins.get(lib))) return true;
		}

		return false;
	}

	public boolean has(L lib) {
		return origins.containsKey(lib);
	}

	public Path getOrigin(L lib) {
		return origins.get(lib);
	}

	public String getLocalPath(L lib) {
		return localPaths.get(lib);
	}

	public String getClassName(L lib) {
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

		for (Iterator<Map.Entry<L, Path>> it = origins.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<L, Path> entry = it.next();

			if (entry.getValue().equals(path)) {
				localPaths.remove(entry.getKey());
				it.remove();

				ret = true;
			}
		}

		return ret;
	}

	public interface LibraryType {
		boolean isInEnv(EnvType env);
		String[] getPaths();
	}
}
