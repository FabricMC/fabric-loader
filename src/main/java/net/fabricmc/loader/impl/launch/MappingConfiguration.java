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

package net.fabricmc.loader.impl.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.zip.ZipError;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.FilteringMappingVisitor;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MappingConfiguration {
	private static final boolean FIX_PACKAGE_ACCESS = System.getProperty(SystemProperties.FIX_PACKAGE_ACCESS) != null;

	private boolean initializedMetadata;
	private boolean initializedMappings;

	@Nullable
	private String gameId;
	@Nullable
	private String gameVersion;
	@Nullable
	private List<String> namespaces;
	@Nullable
	private MemoryMappingTree mappings;

	@Nullable
	public String getGameId() {
		initializeMetadata();

		return gameId;
	}

	@Nullable
	public String getGameVersion() {
		initializeMetadata();

		return gameVersion;
	}

	@Nullable
	public List<String> getNamespaces() {
		initializeMetadata();

		return namespaces;
	}

	public boolean matches(String gameId, String gameVersion) {
		initializeMetadata();

		return (this.gameId == null || gameId == null || gameId.equals(this.gameId))
				&& (this.gameVersion == null || gameVersion == null || gameVersion.equals(this.gameVersion));
	}

	public MappingTree getMappings() {
		initializeMappings();

		return mappings;
	}

	public String getTargetNamespace() {
		return FabricLauncherBase.getLauncher().isDevelopment() ? "named" : "intermediary";
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return FIX_PACKAGE_ACCESS || getTargetNamespace().equals("named");
	}

	private void initializeMetadata() {
		if (initializedMetadata) return;

		final URLConnection connection = openMappings();

		try {
			if (connection != null) {
				if (connection instanceof JarURLConnection) {
					final Manifest manifest = ((JarURLConnection) connection).getManifest();

					if (manifest != null) {
						gameId = ManifestUtil.getManifestValue(manifest, new Name("Game-Id"));
						gameVersion = ManifestUtil.getManifestValue(manifest, new Name("Game-Version"));
					}
				}

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					final MappingFormat format = readMappingFormat(reader);

					switch (format) {
					case TINY_FILE:
						namespaces = Tiny1FileReader.getNamespaces(reader);
						break;
					case TINY_2_FILE:
						namespaces = Tiny2FileReader.getNamespaces(reader);
						break;
					default:
						throw new UnsupportedOperationException("Unsupported mapping format: " + format);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error reading mapping metadata", e);
		}

		initializedMetadata = true;
	}

	private void initializeMappings() {
		if (initializedMappings) return;

		initializeMetadata();
		final URLConnection connection = openMappings();

		if (connection != null) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				long time = System.currentTimeMillis();
				mappings = new MemoryMappingTree();
				final FilteringMappingVisitor mappingFilter = new FilteringMappingVisitor(mappings);

				final MappingFormat format = readMappingFormat(reader);

				switch (format) {
				case TINY_FILE:
					Tiny1FileReader.read(reader, mappingFilter);
					break;
				case TINY_2_FILE:
					Tiny2FileReader.read(reader, mappingFilter);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported mapping format: " + format);
				}

				Log.debug(LogCategory.MAPPINGS, "Loading mappings took %d ms", System.currentTimeMillis() - time);
			} catch (IOException e) {
				throw new RuntimeException("Error reading mappings", e);
			}
		}

		if (mappings == null) {
			Log.info(LogCategory.MAPPINGS, "Mappings not present!");
			mappings = new MemoryMappingTree();
		}

		initializedMappings = true;
	}

	@Nullable
	private URLConnection openMappings() {
		URL url = MappingConfiguration.class.getClassLoader().getResource("mappings/mappings.tiny");

		if (url != null) {
			try {
				return url.openConnection();
			} catch (IOException | ZipError e) {
				throw new RuntimeException("Error reading "+url, e);
			}
		}

		return null;
	}

	private MappingFormat readMappingFormat(BufferedReader reader) throws IOException {
		// We will only ever need to read tiny here
		// so to strip the other formats from the included copy of mapping IO, don't use MappingReader.read()
		reader.mark(4096);
		final MappingFormat format = MappingReader.detectFormat(reader);
		reader.reset();

		return format;
	}
}
