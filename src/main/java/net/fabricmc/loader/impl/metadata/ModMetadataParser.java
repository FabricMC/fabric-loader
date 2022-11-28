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

package net.fabricmc.loader.impl.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class ModMetadataParser {
	public static final int LATEST_VERSION = 1;
	/**
	 * Keys that will be ignored by any mod metadata parser.
	 */
	public static final Set<String> IGNORED_KEYS = Collections.singleton("$schema");

	// Per the ECMA-404 (www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf), the JSON spec does not prohibit duplicate keys.
	// For all intents and purposes of replicating the logic of Gson's fromJson before we have migrated to JsonReader, duplicate keys will replace previous entries.
	public static LoaderModMetadata parseMetadata(InputStream is, String modPath, List<String> modParentPaths,
			VersionOverrides versionOverrides, DependencyOverrides depOverrides, boolean isDevelopment) throws ParseMetadataException {
		try {
			LoaderModMetadata ret = readModMetadata(is, isDevelopment);

			versionOverrides.apply(ret);
			depOverrides.apply(ret);

			MetadataVerifier.verify(ret, isDevelopment);

			return ret;
		} catch (ParseMetadataException e) {
			e.setModPaths(modPath, modParentPaths);
			throw e;
		} catch (Throwable t) {
			ParseMetadataException e = new ParseMetadataException(t);
			e.setModPaths(modPath, modParentPaths);
			throw e;
		}
	}

	private static LoaderModMetadata readModMetadata(InputStream is, boolean isDevelopment) throws IOException, ParseMetadataException {
		// So some context:
		// Per the json specification, ordering of fields is not typically enforced.
		// Furthermore we cannot guarantee the `schemaVersion` is the first field in every `fabric.mod.json`
		//
		// To work around this, we do the following:
		// Try to read first field
		// If the first field is the schemaVersion, read the file normally.
		//
		// If the first field is not the schema version, fallback to a more exhaustive check.
		// Read the rest of the file, looking for the `schemaVersion` field.
		// If we find the field, cache the value
		// If there happens to be another `schemaVersion` that has a differing value, then fail.
		// At the end, if we find no `schemaVersion` then assume the `schemaVersion` is 0
		// Re-read the JSON file.
		int schemaVersion = 0;

		try (JsonReader reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			reader.setRewindEnabled(true);

			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new ParseMetadataException("Root of \"fabric.mod.json\" must be an object", reader);
			}

			reader.beginObject();

			boolean firstField = true;

			while (reader.hasNext()) {
				// Try to read the schemaVersion
				String key = reader.nextName();

				if (key.equals("schemaVersion")) {
					if (reader.peek() != JsonToken.NUMBER) {
						throw new ParseMetadataException("\"schemaVersion\" must be a number.", reader);
					}

					schemaVersion = reader.nextInt();

					if (firstField) {
						reader.setRewindEnabled(false);
						// Finish reading the metadata
						LoaderModMetadata ret = readModMetadata(reader, schemaVersion);
						reader.endObject();

						return ret;
					}

					// schemaVersion found, but after some content -> start over to parse all data with the detected version
					break;
				} else {
					reader.skipValue();
				}

				if (!IGNORED_KEYS.contains(key)) {
					firstField = false;
				}
			}

			// Slow path, schema version wasn't specified early enough, re-read with detected/inferred version

			reader.rewind();
			reader.setRewindEnabled(false);

			reader.beginObject();
			LoaderModMetadata ret = readModMetadata(reader, schemaVersion);
			reader.endObject();

			if (isDevelopment) {
				Log.warn(LogCategory.METADATA, "\"fabric.mod.json\" from mod %s did not have \"schemaVersion\" as first field.", ret.getId());
			}

			return ret;
		}
	}

	private static LoaderModMetadata readModMetadata(JsonReader reader, int schemaVersion) throws IOException, ParseMetadataException {
		switch (schemaVersion) {
		case 1:
			return V1ModMetadataParser.parse(reader);
		case 0:
			return V0ModMetadataParser.parse(reader);
		default:
			if (schemaVersion > 0) {
				throw new ParseMetadataException(String.format("This version of fabric-loader doesn't support the newer schema version of \"%s\""
						+ "\nPlease update fabric-loader to be able to read this.", schemaVersion));
			}

			throw new ParseMetadataException(String.format("Invalid/Unsupported schema version \"%s\" was found", schemaVersion));
		}
	}

	static void logWarningMessages(String id, List<ParseWarning> warnings) {
		if (warnings.isEmpty()) return;

		final StringBuilder message = new StringBuilder();

		message.append(String.format("The mod \"%s\" contains invalid entries in its mod json:", id));

		for (ParseWarning warning : warnings) {
			message.append(String.format("\n- %s \"%s\" at line %d column %d",
					warning.getReason(), warning.getKey(), warning.getLine(), warning.getColumn()));
		}

		Log.warn(LogCategory.METADATA, message.toString());
	}

	private ModMetadataParser() {
	}
}
