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

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.util.mappings.MixinIntermediaryDevRemapper;
import net.fabricmc.mapping.tree.TinyTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.util.List;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() {

	}

	/**
	 * Internal. Mixin configs are added in the form "mod:&lt;modid&gt;:&lt;mixinname&gt;"
	 */
	public static final String MOD_PREFIX = "mod:";

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|MixinBootstrap");
	private static boolean initialized = false;

	static void addConfiguration(String modId, String configuration) {
		Mixins.addConfiguration(MOD_PREFIX + modId + ":" + configuration);
	}

	public static void init(EnvType side, FabricLoader loader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration mappingConfiguration = FabricLauncherBase.getLauncher().getMappingConfiguration();
			TinyTree mappings = mappingConfiguration.getMappings();

			if (mappings != null) {
				List<String> namespaces = mappings.getMetadata().getNamespaces();
				if (namespaces.contains("intermediary") && namespaces.contains(mappingConfiguration.getTargetNamespace())) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, "intermediary", mappingConfiguration.getTargetNamespace());
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						LOGGER.info("Loaded Fabric development mappings for mixin remapper!");
					} catch (Exception e) {
						LOGGER.error("Fabric development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				}
			}
		}

		MixinBootstrap.init();

		for (ModContainer mod : loader.getAllMods()) {
			ModMetadata meta = mod.getMetadata();

			if (meta instanceof LoaderModMetadata) {
				for (String config : ((LoaderModMetadata) meta).getMixinConfigs(side)) {
					addConfiguration(meta.getId(), config);
				}
			}
		}

		initialized = true;
	}
}
