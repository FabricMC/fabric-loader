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

package net.fabricmc.loader.impl.util.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public final class VersionParser {
	public static Version parse(String s, boolean storeX) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}

		Version version;

		try {
			version = new SemanticVersionImpl(s, storeX);
		} catch (VersionParsingException e) {
			version = new StringVersion(s);
		}

		return version;
	}

	public static SemanticVersion parseSemantic(String s) throws VersionParsingException {
		if (s == null || s.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}

		return new SemanticVersionImpl(s, false);
	}
}
