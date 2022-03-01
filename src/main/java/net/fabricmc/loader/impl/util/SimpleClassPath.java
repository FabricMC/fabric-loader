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
	public void close() {
		for (int i = 0; i < openJars.length; i++) {
			Closeable file = openJars[i];

			try {
				if (file != null) file.close();
			} catch (IOException e) { }

			openJars[i] = null;
		}
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
						throw new IOException(String.format("error opening %s: %s", path.toAbsolutePath(), e), e);
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
	private final boolean[] jarMarkers;
	private final ZipFile[] openJars;
}
