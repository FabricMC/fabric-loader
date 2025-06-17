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

import java.io.File;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

public final class UrlUtil {
	public static final Path LOADER_CODE_SOURCE = getCodeSource(UrlUtil.class);

	public static Path getCodeSource(URL url, String localPath) throws UrlConversionException {
		try {
			URLConnection connection = url.openConnection();

			if (connection instanceof JarURLConnection) {
				return asPath(((JarURLConnection) connection).getJarFileURL());
			} else {
				URI uri = url.toURI();
				String path = uri.getPath(); // URI.getPath decodes percent-encoding etc unlike URL.getPath or URI.getRawPath

				if (path.endsWith(localPath)) {
					String basePath = path.substring(0, path.length() - localPath.length()); // keep trailing / in case it's standalone (root dir)
					URI baseUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), basePath, uri.getQuery(), uri.getFragment());

					return Paths.get(baseUri);
				} else {
					throw new UrlConversionException("Could not figure out code source for file '" + localPath + "' in URL '" + url + "'!");
				}
			}
		} catch (Exception e) {
			throw new UrlConversionException(e);
		}
	}

	public static Path asPath(URL url) {
		try {
			return Paths.get(url.toURI());
		} catch (URISyntaxException e) {
			throw ExceptionUtil.wrap(e);
		}
	}

	public static URL asUrl(File file) throws MalformedURLException {
		return file.toURI().toURL();
	}

	public static URL asUrl(Path path) throws MalformedURLException {
		return path.toUri().toURL();
	}

	public static Path getCodeSource(Class<?> cls) {
		CodeSource cs = cls.getProtectionDomain().getCodeSource();
		if (cs == null) return null;

		return asPath(cs.getLocation());
	}
}
