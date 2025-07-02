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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.FilteringMappingVisitor;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class MappingConfiguration {
	private static final boolean FIX_PACKAGE_ACCESS = SystemProperties.isSet(SystemProperties.FIX_PACKAGE_ACCESS);

	// same ns between client and server
	public static final String OFFICIAL_NAMESPACE = "official";
	// separate client/server ns
	public static final String CLIENT_OFFICIAL_NAMESPACE = "clientOfficial";
	public static final String SERVER_OFFICIAL_NAMESPACE = "serverOfficial";

	public static final String INTERMEDIARY_NAMESPACE = "intermediary";
	public static final String NAMED_NAMESPACE = "named";

	private boolean initializedMetadata;
	private boolean initializedMappings;
	private MappingSource mappingSource;

	private String namespace;

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
		initializeMappings(true);

		return gameId;
	}

	@Nullable
	public String getGameVersion() {
		initializeMappings(true);

		return gameVersion;
	}

	@Nullable
	public List<String> getNamespaces() {
		initializeMappings(true);

		return namespaces;
	}

	public boolean matches(String gameId, String gameVersion) {
		initializeMappings(true);

		return (this.gameId == null || gameId == null || gameId.equals(this.gameId))
				&& (this.gameVersion == null || gameVersion == null || gameVersion.equals(this.gameVersion));
	}

	public MappingTree getMappings() {
		initializeMappings(false);

		return mappings;
	}

	public String getRuntimeNamespace() {
		if (namespace == null) {
			namespace = FabricLoaderImpl.INSTANCE.getGameProvider().getRuntimeNamespace(FabricLauncherBase.getLauncher().getDefaultRuntimeNamespace());
		}

		return namespace;
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return FIX_PACKAGE_ACCESS || getRuntimeNamespace().equals(NAMED_NAMESPACE);
	}

	private void initializeMappings(boolean metaOnly) {
		if (initializedMappings || initializedMetadata && metaOnly) return;

		long time = System.nanoTime();
		MappingSource source = getMappingSource();
		MappingVisitor out;

		if (metaOnly) {
			out = null;
		} else {
			mappings = new MemoryMappingTree();
			out = new FilteringMappingVisitor(mappings);
		}

		try {
			if (source.path != null) {
				if (metaOnly) {
					namespaces = MappingReader.getNamespaces(source.path);
				} else {
					MappingReader.read(source.path, out);
				}
			} else if (source.url != null) {
				URLConnection connection = source.url.openConnection();

				if (!initializedMetadata && connection instanceof JarURLConnection) {
					final Manifest manifest = ((JarURLConnection) connection).getManifest();

					if (manifest != null) {
						gameId = ManifestUtil.getManifestValue(manifest, new Name("Game-Id"));
						gameVersion = ManifestUtil.getManifestValue(manifest, new Name("Game-Version"));
					}
				}

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
					if (metaOnly) {
						namespaces = MappingReader.getNamespaces(reader);
					} else {
						MappingReader.read(reader, out);
					}
				}
			} else { // no mappings
				Log.info(LogCategory.MAPPINGS, "Mappings not present!");
				mappings = new MemoryMappingTree();
				initializedMappings = true;
			}
		} catch (IOException e) {
			throw new RuntimeException("Error reading mappings", e);
		}

		if (!initializedMetadata && !metaOnly && mappings.getSrcNamespace() != null) { // copy namespaces from mapping tree if metadata wasn't initialized separately
			namespaces = new ArrayList<>(mappings.getDstNamespaces().size() + 1);
			namespaces.add(mappings.getSrcNamespace());
			namespaces.addAll(mappings.getDstNamespaces());
		}

		Log.debug(LogCategory.MAPPINGS, "Loading mappings%s took %.2f ms", (metaOnly ? " (meta only)" : ""), (System.nanoTime() - time) * 1e-6);

		initializedMetadata = true;
		if (!metaOnly) initializedMappings = true;
	}

	private MappingSource getMappingSource() {
		if (mappingSource != null) return mappingSource;

		final String zipMappingPath = "mappings/mappings.tiny";
		String pathStr = System.getProperty(SystemProperties.MAPPING_PATH);
		URL url = null;
		Path path = null;

		if (pathStr == null) {
			url = MappingConfiguration.class.getClassLoader().getResource(zipMappingPath);
		} else {
			path = Paths.get(pathStr).toAbsolutePath();

			if (!Files.exists(path)) {
				Log.warn(LogCategory.MAPPINGS, "Mapping file %s supplied by the system property doesn't exist", path);
				path = null;
			} else if (!Files.isDirectory(path)) { // check for zip packaging
				try (ZipFile zf = new ZipFile(path.toFile())) {
					ZipEntry entry = zf.getEntry(zipMappingPath);

					if (entry == null) {
						Log.warn(LogCategory.MAPPINGS, "Mapping file %s supplied by the system property doesn't contain mappings at "+zipMappingPath, path);
						path = null;
					} else {
						// zip packaging confirmed, turn into nested URL
						// this ensures initializeMappings will try to read the manifest

						url = new URI("jar", path.toUri() + "!/" + zipMappingPath, null).toURL();
						path = null;
					}
				} catch (ZipException e) {
					// presumably not a zip, keep plain path
				} catch (IOException | URISyntaxException e) {
					throw new RuntimeException(e);
				}
			}
		}

		mappingSource = new MappingSource(url, path);

		return mappingSource;
	}

	private static final class MappingSource {
		final URL url;
		final Path path;

		MappingSource(URL url, Path path) {
			this.url = url;
			this.path = path;
		}
	}
}
