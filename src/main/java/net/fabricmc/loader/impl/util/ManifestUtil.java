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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

public final class ManifestUtil {
	public static Manifest readManifest(Class<?> cls) throws IOException, URISyntaxException {
		CodeSource cs = cls.getProtectionDomain().getCodeSource();
		if (cs == null) return null;

		URL url = cs.getLocation();
		if (url == null) return null;

		return readManifest(url);
	}

	private static Manifest readManifest(URL codeSourceUrl) throws IOException, URISyntaxException {
		Path path = UrlUtil.asPath(codeSourceUrl);

		if (Files.isDirectory(path)) {
			return readManifestFromBasePath(path);
		} else {
			URLConnection connection = new URL("jar:" + codeSourceUrl.toString() + "!/").openConnection();

			if (connection instanceof JarURLConnection) {
				return ((JarURLConnection) connection).getManifest();
			}

			try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(path, false)) {
				return readManifestFromBasePath(jarFs.get().getRootDirectories().iterator().next());
			}
		}
	}

	public static Manifest readManifest(Path codeSource) throws IOException {
		if (Files.isDirectory(codeSource)) {
			return readManifestFromBasePath(codeSource);
		} else {
			try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(codeSource, false)) {
				return readManifestFromBasePath(jarFs.get().getRootDirectories().iterator().next());
			}
		}
	}

	public static Manifest readManifestFromBasePath(Path basePath) throws IOException {
		Path path = basePath.resolve("META-INF").resolve("MANIFEST.MF");
		if (!Files.exists(path)) return null;

		try (InputStream stream = Files.newInputStream(path)) {
			return new Manifest(stream);
		}
	}

	public static String getManifestValue(Manifest manifest, Name name) {
		return manifest.getMainAttributes().getValue(name);
	}

	public static List<URL> getClassPath(Manifest manifest, Path baseDir) throws MalformedURLException {
		String cp = ManifestUtil.getManifestValue(manifest, Name.CLASS_PATH);
		if (cp == null) return null;

		StringTokenizer tokenizer = new StringTokenizer(cp);
		List<URL> ret = new ArrayList<>();
		URL context = UrlUtil.asUrl(baseDir);

		while (tokenizer.hasMoreElements()) {
			ret.add(new URL(context, tokenizer.nextToken()));
		}

		return ret;
	}
}
