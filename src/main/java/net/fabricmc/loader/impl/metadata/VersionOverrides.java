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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.version.VersionParser;

public final class VersionOverrides {
	private final Map<String, Version> replacements = new HashMap<>();

	public VersionOverrides() {
		String property = System.getProperty(SystemProperties.DEBUG_REPLACE_VERSION);
		if (property == null) return;

		for (String entry : property.split(",")) {
			int pos = entry.indexOf(":");
			if (pos <= 0 || pos >= entry.length() - 1) throw new RuntimeException("invalid version replacement entry: "+entry);

			String id = entry.substring(0, pos);
			String rawVersion = entry.substring(pos + 1);
			Version version;

			try {
				version = VersionParser.parse(rawVersion, false);
			} catch (VersionParsingException e) {
				throw new RuntimeException(String.format("Invalid replacement version for mod %s: %s", id, rawVersion), e);
			}

			replacements.put(id, version);
		}
	}

	public void apply(LoaderModMetadata metadata) {
		if (replacements.isEmpty()) return;

		Version replacement = replacements.get(metadata.getId());

		if (replacement != null) {
			metadata.setVersion(replacement);
		}
	}

	public Collection<String> getAffectedModIds() {
		return replacements.keySet();
	}
}
