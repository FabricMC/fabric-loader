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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * Internal variant of the ModMetadata interface.
 */
public interface LoaderModMetadata extends ModMetadata {
	int getSchemaVersion();

	boolean loadsInEnvironment(EnvType type);
	Collection<NestedJarEntry> getJars();
	Collection<String> getMixinConfigs(EnvType type);
	String getAccessWidener();

	Map<String, String> getLanguageAdapterDefinitions();
	List<EntrypointMetadata> getEntrypoints(String type);
	Collection<String> getEntrypointKeys();

	void emitFormatWarnings(Logger logger);
}
