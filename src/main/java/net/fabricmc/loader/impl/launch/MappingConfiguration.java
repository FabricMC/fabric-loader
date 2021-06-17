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
import java.io.InputStream;
import java.io.InputStreamReader;

import net.fabricmc.loader.util.log.Log;
import net.fabricmc.loader.util.log.LogCategory;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MappingConfiguration {
	private static TinyTree mappings;
	private static boolean checkedMappings;

	public TinyTree getMappings() {
		if (!checkedMappings) {
			InputStream mappingStream = FabricLauncherBase.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");

			if (mappingStream != null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(mappingStream))) {
					long time = System.currentTimeMillis();
					mappings = TinyMappingFactory.loadWithDetection(reader);
					Log.debug(LogCategory.MAPPINGS, "Loading mappings took %d ms", System.currentTimeMillis() - time);
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
				Log.info(LogCategory.MAPPINGS, "Mappings not present!");
				mappings = TinyMappingFactory.EMPTY_TREE;
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
