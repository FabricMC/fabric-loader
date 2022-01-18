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
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.zip.ZipError;

import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public final class MappingConfiguration {
	private boolean initialized;

	private String gameId;
	private String gameVersion;
	private TinyTree mappings;

	public String getGameId() {
		initialize();

		return gameId;
	}

	public String getGameVersion() {
		initialize();

		return gameVersion;
	}

	public boolean matches(String gameId, String gameVersion) {
		initialize();

		return (this.gameId == null || gameId == null || gameId.equals(this.gameId))
				&& (this.gameVersion == null || gameVersion == null || gameVersion.equals(this.gameVersion));
	}

	public TinyTree getMappings() {
		initialize();

		return mappings;
	}

	public String getTargetNamespace() {
		return FabricLauncherBase.getLauncher().isDevelopment() ? "named" : "intermediary";
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return getTargetNamespace().equals("named");
	}

	private void initialize() {
		if (initialized) return;

		URL url = MappingConfiguration.class.getClassLoader().getResource("mappings/mappings.tiny");

		if (url != null) {
			try {
				URLConnection connection = url.openConnection();

				if (connection instanceof JarURLConnection) {
					Manifest manifest = ((JarURLConnection) connection).getManifest();

					if (manifest != null) {
						gameId = ManifestUtil.getManifestValue(manifest, new Name("Game-Id"));
						gameVersion = ManifestUtil.getManifestValue(manifest, new Name("Game-Version"));
					}
				}

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					long time = System.currentTimeMillis();
					mappings = TinyMappingFactory.loadWithDetection(reader);
					Log.debug(LogCategory.MAPPINGS, "Loading mappings took %d ms", System.currentTimeMillis() - time);
				}
			} catch (IOException | ZipError e) {
				throw new RuntimeException("Error reading "+url, e);
			}
		}

		if (mappings == null) {
			Log.info(LogCategory.MAPPINGS, "Mappings not present!");
			mappings = TinyMappingFactory.EMPTY_TREE;
		}

		initialized = true;
	}
}
