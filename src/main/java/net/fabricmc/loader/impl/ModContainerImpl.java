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

package net.fabricmc.loader.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModOriginImpl;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

@SuppressWarnings("deprecation")
public class ModContainerImpl extends net.fabricmc.loader.ModContainer {
	private final LoaderModMetadata info;
	private final ModOrigin origin;
	private final List<Path> codeSourcePaths;
	private final String parentModId;
	private final Collection<String> childModIds;

	private volatile List<Path> roots;

	public ModContainerImpl(ModCandidateImpl candidate) {
		this.info = candidate.getMetadata();
		this.codeSourcePaths = candidate.getPaths();
		this.parentModId = candidate.getParentMods().isEmpty() ? null : candidate.getParentMods().iterator().next().getId();
		this.childModIds = candidate.getNestedMods().isEmpty() ? Collections.emptyList() : new ArrayList<>(candidate.getNestedMods().size());

		for (ModCandidateImpl c : candidate.getNestedMods()) {
			if (c.getParentMods().size() <= 1 || c.getParentMods().iterator().next() == candidate) {
				childModIds.add(c.getId());
			}
		}

		List<Path> paths = candidate.getOriginPaths();
		this.origin = paths != null ? new ModOriginImpl(paths) : new ModOriginImpl(parentModId, candidate.getLocalPath());
	}

	@Override
	public LoaderModMetadata getMetadata() {
		return info;
	}

	@Override
	public ModOrigin getOrigin() {
		return origin;
	}

	@Override
	public List<Path> getCodeSourcePaths() {
		return codeSourcePaths;
	}

	@Override
	public Path getRootPath() {
		List<Path> paths = getRootPaths();

		if (paths.size() != 1 && !warnedMultiPath) {
			if (!FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()) warnedMultiPath = true;
			Log.warn(LogCategory.GENERAL, "getRootPath access for %s with multiple paths, returning only one which may incur unexpected behavior!", this);
		}

		return paths.get(0);
	}

	private static boolean warnedMultiPath = false;

	@Override
	public List<Path> getRootPaths() {
		List<Path> ret = roots;

		if (ret == null || !checkFsOpen(ret)) {
			roots = ret = obtainRootPaths(); // obtainRootPaths is thread safe, but we need to avoid plain or repeated reads to root
		}

		return ret;
	}

	private boolean checkFsOpen(List<Path> paths) {
		for (Path path : paths) {
			if (path.getFileSystem().isOpen()) continue;

			if (!warnedClose) {
				if (!FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()) warnedClose = true;
				Log.warn(LogCategory.GENERAL, "FileSystem for %s has been closed unexpectedly, existing root path references may break!", this);
			}

			return false;
		}

		return true;
	}

	private boolean warnedClose = false;

	private List<Path> obtainRootPaths() {
		boolean allDirs = true;

		for (Path path : codeSourcePaths) {
			if (!Files.isDirectory(path)) {
				allDirs = false;
				break;
			}
		}

		if (allDirs) return codeSourcePaths;

		try {
			if (codeSourcePaths.size() == 1) {
				return Collections.singletonList(obtainRootPath(codeSourcePaths.get(0)));
			} else {
				List<Path> ret = new ArrayList<>(codeSourcePaths.size());

				for (Path path : codeSourcePaths) {
					ret.add(obtainRootPath(path));
				}

				return Collections.unmodifiableList(ret);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to obtain root directory for mod '" + info.getId() + "'!", e);
		}
	}

	private static Path obtainRootPath(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return path;
		} else /* JAR */ {
			FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(path, false);
			FileSystem fs = delegate.get();

			if (fs == null) {
				throw new RuntimeException("Could not open JAR file " + path + " for NIO reading!");
			}

			return fs.getRootDirectories().iterator().next();

			// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
		}
	}

	@Override
	public Path getPath(String file) {
		Optional<Path> res = findPath(file);
		if (res.isPresent()) return res.get();

		List<Path> roots = this.roots;

		if (!roots.isEmpty()) {
			Path root = roots.get(0);

			return root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
		} else {
			return Paths.get(".").resolve("missing_ae236f4970ce").resolve(file.replace('/', File.separatorChar)); // missing dummy path
		}
	}

	@Override
	public Optional<ModContainer> getContainingMod() {
		return parentModId != null ? FabricLoaderImpl.INSTANCE.getModContainer(parentModId) : Optional.empty();
	}

	@Override
	public Collection<ModContainer> getContainedMods() {
		if (childModIds.isEmpty()) return Collections.emptyList();

		List<ModContainer> ret = new ArrayList<>(childModIds.size());

		for (String id : childModIds) {
			ModContainer mod = FabricLoaderImpl.INSTANCE.getModContainer(id).orElse(null);
			if (mod != null) ret.add(mod);
		}

		return ret;
	}

	@Deprecated
	@Override
	public LoaderModMetadata getInfo() {
		return info;
	}

	@Override
	public String toString() {
		return String.format("%s %s", info.getId(), info.getVersion());
	}
}
