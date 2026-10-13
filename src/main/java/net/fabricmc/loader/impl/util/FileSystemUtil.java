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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipError;

public final class FileSystemUtil {
	public static class FileSystemDelegate implements AutoCloseable {
		private final FileSystem fileSystem;
		private final boolean owner;

		public FileSystemDelegate(FileSystem fileSystem, boolean owner) {
			this.fileSystem = fileSystem;
			this.owner = owner;
		}

		public FileSystem get() {
			return fileSystem;
		}

		@Override
		public void close() throws IOException {
			if (owner) {
				fileSystem.close();
			}
		}
	}

	private FileSystemUtil() { }

	public enum AccessMode {
		READ_ONLY("accessMode", "readOnly"), // only supported by ZipFileSystem in Java 25+
		READ_WRITE(),
		READ_WRITE_CREATE("create", "true");

		final Map<String, String> fsArgs;

		AccessMode(String... args) {
			Map<String, String> argsMap;

			if (args.length == 0) {
				argsMap = Collections.emptyMap();
			} else {
				argsMap = new HashMap<>(args.length / 2);

				for (int i = 0; i < args.length; i += 2) {
					argsMap.put(args[i], args[i + 1]);
				}
			}

			this.fsArgs = argsMap;
		}
	}

	public static FileSystemDelegate getJarFileSystem(Path path, AccessMode accessMode) throws IOException {
		return getJarFileSystem(path.toUri(), accessMode);
	}

	public static FileSystemDelegate getJarFileSystem(URI uri, AccessMode accessMode) throws IOException {
		URI jarUri;

		try {
			jarUri = new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

		boolean opened = false;
		FileSystem ret = null;

		try {
			ret = FileSystems.getFileSystem(jarUri);
		} catch (FileSystemNotFoundException ignore) {
			try {
				ret = FileSystems.newFileSystem(jarUri, accessMode.fsArgs);
				opened = true;
			} catch (FileSystemAlreadyExistsException ignore2) {
				ret = FileSystems.getFileSystem(jarUri);
			} catch (IOException | ZipError e) {
				throw new IOException("Error accessing "+uri+": "+e, e);
			}
		}

		if (!opened && accessMode != AccessMode.READ_ONLY && ret.isReadOnly()) {
			throw new IllegalStateException(String.format("file %s is already open read-only and can't be reopened for writing", uri));
		}

		return new FileSystemDelegate(ret, opened);
	}
}
