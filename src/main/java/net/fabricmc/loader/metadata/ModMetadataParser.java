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

package net.fabricmc.loader.metadata;

import com.google.gson.*;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.util.version.VersionDeserializer;

import java.io.InputStream;
import java.io.InputStreamReader;

public class ModMetadataParser {
	public static final int LATEST_VERSION = 1;

	private static final Gson GSON_V1 = new GsonBuilder()
		.registerTypeAdapter(Version.class, new VersionDeserializer())
		.registerTypeAdapter(ModMetadataV1.JarEntry.class, new ModMetadataV1.JarEntry.Deserializer())
		.registerTypeAdapter(ModMetadataV1.IconEntry.class, new ModMetadataV1.IconEntry.Deserializer())
		.registerTypeAdapter(ModMetadataV1.LicenseEntry.class, new ModMetadataV1.LicenseEntry.Deserializer())
		.registerTypeAdapter(ModMetadataV1.Person.class, new ModMetadataV1.Person.Deserializer())
		.registerTypeAdapter(ModMetadataV1.DependencyContainer.class, new ModMetadataV1.DependencyContainer.Deserializer())
		.registerTypeAdapter(ModMetadataV1.MixinEntry.class, new ModMetadataV1.MixinEntry.Deserializer())
		.registerTypeAdapter(ModMetadataV1.EntrypointContainer.class, new ModMetadataV1.EntrypointContainer.Deserializer())
		.registerTypeAdapter(ModMetadataV1.Environment.class, new ModMetadataV1.Environment.Deserializer())
		.create();

	private static LoaderModMetadata getMod(FabricLoader loader, JsonObject object) {
		if (!object.has("schemaVersion")) {
			throw new UnsupportedOperationException("Loader 0.3 era fabric.mod.jsons (without a schemaVersion) are no longer supported");
		} else {
			//noinspection SwitchStatementWithTooFewBranches
			switch (object.get("schemaVersion").getAsInt()) {
				case 1:
					return GSON_V1.fromJson(object, ModMetadataV1.class);
				default:
					loader.getLogger().warn("Mod ID " + (object.has("id") ? object.get("id").getAsString() : "<unknown>") + " has invalid schema version: " + object.get("schemaVersion").getAsInt());
					return null;
			}
		}
	}

	public static LoaderModMetadata[] getMods(FabricLoader loader, InputStream in) {
		JsonElement el = JsonParser.parseReader(new InputStreamReader(in));
		if (el.isJsonObject()) {
			LoaderModMetadata metadata = getMod(loader, el.getAsJsonObject());
			if (metadata != null) {
				return new LoaderModMetadata[] { metadata };
			}
		}

		return new LoaderModMetadata[0];
	}
}
