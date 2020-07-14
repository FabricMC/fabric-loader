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

package net.fabricmc.loader.util.version;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.lang.reflect.Type;

public class VersionDeserializer implements JsonDeserializer<Version> {
	public static SemanticVersion deserializeSemantic(String s) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}

		return new SemanticVersionImpl(s, false);
	}

	public static Version deserialize(String s) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}

		Version version;

		try {
			version = new SemanticVersionImpl(s, false);
		} catch (VersionParsingException e) {
			version = new StringVersion(s);
		}

		return version;
	}

	@Override
	public Version deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		if (!json.isJsonPrimitive()) {
			throw new JsonParseException("Version must be a non-empty string!");
		}

		String s = json.getAsString();
		try {
			return deserialize(s);
		} catch (VersionParsingException e) {
			throw new JsonParseException(e);
		}
	}
}
