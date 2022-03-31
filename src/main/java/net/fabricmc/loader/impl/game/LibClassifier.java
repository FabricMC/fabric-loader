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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

import org.sat4j.minisat.SolverFactory;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.LibClassifier.LibraryType;
import net.fabricmc.loader.impl.util.UrlUtil;

public final class LibClassifier<L extends Enum<L> & LibraryType> {
	private final List<L> libs;
	private final List<LoaderLibrary> loaderLibs;
	private final Path gameProviderLib;
	private final Map<L, Path> origins;
	private final Map<L, String> localPaths;
	private final Map<LoaderLibrary, Path> loaderOrigins = new EnumMap<>(LoaderLibrary.class);
	private final List<Path> unmatchedOrigins = new ArrayList<>();

	public LibClassifier(Class<L> cls, EnvType env, GameProvider gameProvider) {
		boolean shaded = UrlUtil.getCodeSource(SolverFactory.class).equals(UrlUtil.LOADER_CODE_SOURCE);

		L[] libs = cls.getEnumConstants();

		this.libs = new ArrayList<>(libs.length);
		this.origins = new EnumMap<>(cls);
		this.localPaths = new EnumMap<>(cls);

		for (L lib : libs) {
			if (lib.isApplicable(env, shaded)) {
				this.libs.add(lib);
			}
		}

		LoaderLibrary[] loaderLibs = LoaderLibrary.values();
		this.loaderLibs = new ArrayList<>(loaderLibs.length);

		for (LoaderLibrary lib : loaderLibs) {
			if (lib.isApplicable(env, shaded)) {
				this.loaderLibs.add(lib);
			}
		}

		this.gameProviderLib = UrlUtil.getCodeSource(gameProvider.getClass());
	}

	public void process(URL url) throws IOException {
		try {
			process(UrlUtil.asPath(url));
		} catch (URISyntaxException e) {
			throw new RuntimeException("invalid url: "+url);
		}
	}

	@SafeVarargs
	public final void process(Iterable<Path> paths, L... excludedLibs) throws IOException {
		Set<L> excluded = makeSet(excludedLibs);

		for (Path path : paths) {
			process(path, excluded);
		}
	}

	@SafeVarargs
	public final void process(Path path, L... excludedLibs) throws IOException {
		process(path, makeSet(excludedLibs));
	}

	private static <L extends Enum<L>> Set<L> makeSet(L[] libs) {
		if (libs.length == 0) return Collections.emptySet();

		Set<L> ret = EnumSet.of(libs[0]);

		for (int i = 1; i < libs.length; i++) {
			ret.add(libs[i]);
		}

		return ret;
	}

	private void process(Path path, Set<L> excludedLibs) throws IOException {
		boolean matched = false;

		if (Files.isDirectory(path)) {
			for (L lib : libs) {
				if (excludedLibs.contains(lib) || origins.containsKey(lib)) continue;

				for (String p : lib.getPaths()) {
					if (Files.exists(path.resolve(p))) {
						matched = true;
						addLibrary(lib, path, p);
						break;
					}
				}
			}

			for (LoaderLibrary lib : loaderLibs) {
				if (!loaderOrigins.containsKey(lib)
						&& Files.exists(path.resolve(lib.path))) {
					loaderOrigins.put(lib, path);
					matched = true;
					break;
				}
			}
		} else {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				for (L lib : libs) {
					if (excludedLibs.contains(lib) || origins.containsKey(lib)) continue;

					for (String p : lib.getPaths()) {
						if (zf.getEntry(p) != null) {
							matched = true;
							addLibrary(lib, path, p);
							break;
						}
					}
				}

				for (LoaderLibrary lib : loaderLibs) {
					if (!loaderOrigins.containsKey(lib)
							&& zf.getEntry(lib.path) != null) {
						loaderOrigins.put(lib, path);
						matched = true;
						break;
					}
				}
			} catch (ZipError | IOException e) {
				throw new IOException("error reading "+path.toAbsolutePath(), e);
			}
		}

		if (!matched) unmatchedOrigins.add(path);
	}

	private void addLibrary(L lib, Path originPath, String localPath) {
		Path prev = origins.put(lib, originPath);
		if (prev != null) throw new IllegalStateException("lib "+lib+" was already added");
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

	public Collection<Path> getLoaderOrigins() {
		Collection<Path> paths = loaderOrigins.values();

		if (paths.contains(gameProviderLib)) {
			return paths;
		} else {
			Collection<Path> ret = new ArrayList<>(paths.size() + 1);
			ret.addAll(paths);
			ret.add(gameProviderLib);

			return ret;
		}
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
		boolean isApplicable(EnvType env, boolean shaded);
		String[] getPaths();
	}
}
