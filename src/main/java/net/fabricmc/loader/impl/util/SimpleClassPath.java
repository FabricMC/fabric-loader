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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

public final class SimpleClassPath implements Closeable {
	public SimpleClassPath(List<Path> paths) {
		this.paths = paths;
		this.jarMarkers = new boolean[paths.size()];
		this.openJars = new ZipFile[paths.size()];

		for (int i = 0; i < jarMarkers.length; i++) {
			if (!Files.isDirectory(paths.get(i))) {
				jarMarkers[i] = true;
			}
		}
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;

		for (int i = 0; i < openJars.length; i++) {
			Closeable file = openJars[i];

			try {
				if (file != null) file.close();
			} catch (IOException e) {
				if (exc == null) {
					exc = e;
				} else {
					exc.addSuppressed(e);
				}
			}

			openJars[i] = null;
		}

		if (exc != null) throw exc;
	}

	public List<Path> getPaths() {
		return paths;
	}

	public CpEntry getEntry(String subPath) throws IOException {
		for (int i = 0; i < jarMarkers.length; i++) {
			if (jarMarkers[i]) {
				ZipFile zf = openJars[i];

				if (zf == null) {
					Path path = paths.get(i);

					try {
						openJars[i] = zf = new ZipFile(path.toFile());
					} catch (IOException | ZipError e) {
						throw new IOException(String.format("error opening %s: %s", LoaderUtil.normalizePath(path), e), e);
					}
				}

				ZipEntry entry = zf.getEntry(subPath);

				if (entry != null) {
					return new CpEntry(i, subPath, entry);
				}
			} else {
				Path file = paths.get(i).resolve(subPath);

				if (Files.isRegularFile(file)) {
					return new CpEntry(i, subPath, file);
				}
			}
		}

		return null;
	}

	public InputStream getInputStream(String subPath) throws IOException {
		CpEntry entry = getEntry(subPath);

		return entry != null ? entry.getInputStream() : null;
	}

	public final class CpEntry {
		private CpEntry(int idx, String subPath, Object instance) {
			this.idx = idx;
			this.subPath = subPath;
			this.instance = instance;
		}

		public Path getOrigin() {
			return paths.get(idx);
		}

		public String getSubPath() {
			return subPath;
		}

		public InputStream getInputStream() throws IOException {
			if (instance instanceof ZipEntry) {
				return openJars[idx].getInputStream((ZipEntry) instance);
			} else {
				return Files.newInputStream((Path) instance);
			}
		}

		@Override
		public String toString() {
			return String.format("%s:%s", getOrigin(), subPath);
		}

		private final int idx;
		private final String subPath;
		private final Object instance;
	}

	private final List<Path> paths;
	private final boolean[] jarMarkers; // whether the path is a jar (otherwise plain dir)
	private final ZipFile[] openJars;
}
