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

package net.fabricmc.loader.launch.common;

import net.fabricmc.mappings.Mappings;
import net.fabricmc.mappings.MappingsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public class MappingConfiguration {
	protected static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");

	private static Mappings mappings;
	private static boolean checkedMappings;

	public Mappings getMappings() {
		if (!checkedMappings) {
			InputStream mappingStream = FabricLauncherBase.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");

			if (mappingStream != null) {
				try {
					long time = System.currentTimeMillis();
					mappings = MappingsProvider.readTinyMappings(mappingStream);
					LOGGER.debug("Loading mappings took " + (System.currentTimeMillis() - time) + " ms");
				} catch (IOException ee) {
					ee.printStackTrace();
				}

				try {
					mappingStream.close();
				} catch (IOException ee) {
					ee.printStackTrace();
				}
			}

			if (mappings == null) {
				mappings = MappingsProvider.createEmptyMappings();
			}

			checkedMappings = true;
		}

		return mappings;
	}

	public String getTargetNamespace() {
		return FabricLauncherBase.getLauncher().isDevelopment() ? "named" : "intermediary";
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return getTargetNamespace().equals("named");
	}
}
