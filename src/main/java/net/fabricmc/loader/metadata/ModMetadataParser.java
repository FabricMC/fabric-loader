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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.lib.gson.JsonReader;
import net.fabricmc.loader.lib.gson.JsonToken;

public final class ModMetadataParser {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final int LATEST_VERSION = 1;

	// Per the ECMA-404 (www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf), the JSON spec does not prohibit duplicate keys.
	// For all intents and purposes of replicating the logic of Gson's fromJson before we have migrated to JsonReader, duplicate keys will replace previous entries.
	public static LoaderModMetadata parseMetadata(Logger logger, Path modJson) throws IOException, ParseMetadataException {
		try {
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

			try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(modJson), StandardCharsets.UTF_8))) {
				if (reader.peek() != JsonToken.BEGIN_OBJECT) {
					throw new ParseMetadataException("Root of \"fabric.mod.json\" must be an object", reader);
				}

				reader.beginObject();

				boolean firstField = true;

				while (reader.hasNext()) {
					// Try to read the schemaVersion
					if (reader.nextName().equals("schemaVersion")) {
						if (reader.peek() != JsonToken.NUMBER) {
							throw new ParseMetadataException("\"schemaVersion\" must be a number.", reader);
						}

						schemaVersion = reader.nextInt();

						if (firstField) {
							// Finish reading the metadata
							return readModMetadata(logger, reader, schemaVersion);
						}

						// schemaVersion found, but after some content -> start over to parse all data with the detected version
					} else {
						reader.skipValue();
					}

					firstField = false;
				}

				reader.endObject();
			}

			// Slow path, schema version wasn't specified early enough, re-read with detected/inferred version
			try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(modJson), StandardCharsets.UTF_8))) {
				// No need to check if the start of the json file as it has already been checked
				reader.beginObject();
				final LoaderModMetadata ret = readModMetadata(logger, reader, schemaVersion);
				reader.endObject();

				if (FabricLoader.getInstance().isDevelopmentEnvironment())
					LOGGER.warn(String.format("\"fabric.mod.json\" from mod %s did not have \"schemaVersion\" as first field.", ret.getId()));
				return ret;
			}
		} catch (IllegalStateException e) {
			// Rethrow Gson's IllegalStateException as a parse exception
			throw new ParseMetadataException(e);
		}
	}

	private static LoaderModMetadata readModMetadata(Logger logger, JsonReader reader, int schemaVersion) throws IOException, ParseMetadataException {
		switch (schemaVersion) {
		case 1:
			return V1ModMetadataParser.parse(logger, reader);
		case 0:
			return V0ModMetadataParser.parse(logger, reader);
		default:
			if (schemaVersion > 0) {
				throw new ParseMetadataException(String.format("This version of fabric-loader doesn't support the newer schema version of \"%s\""
					+ "\nPlease update fabric-loader to be able to read this.", schemaVersion));
			}
			throw new ParseMetadataException(String.format("Invalid/Unsupported schema version \"%s\" was found", schemaVersion));
		}
	}

	static void logWarningMessages(Logger logger, String id, List<ParseWarning> warnings) {
		if (warnings.isEmpty()) {
			return;
		}

		final StringBuilder message = new StringBuilder();

		message.append("The mod \"")
				.append(id)
				.append("\" contains invalid entries in its mod json:");

		for (ParseWarning warning : warnings) {
			message.append("\n- ")
					.append(warning.getReason())
					.append(" \"")
					.append(warning.getKey())
					.append("\" at line ")
					.append(warning.getLine())
					.append(" column ")
					.append(warning.getColumn());
		}

		logger.warn(message.toString());
	}

	private ModMetadataParser() {
	}
}
