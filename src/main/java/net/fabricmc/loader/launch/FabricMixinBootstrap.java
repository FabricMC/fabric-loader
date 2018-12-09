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

package net.fabricmc.loader.launch;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.ModInfo;
import net.fabricmc.loader.util.mixin.MixinIntermediaryDevRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.*;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() {

	}

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|MixinBootstrap");
	private static boolean initialized = false;

	static void addConfiguration(String configuration) {
		Mixins.addConfiguration(configuration);
	}

	static void init(EnvType side, Map<String, String> args, MixinLoader mixinLoader, BufferedReader mappingReader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		if (mappingReader != null) {
			MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper();
			try {
				remapper.readMapping(mappingReader, "intermediary", "named");
				MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
				LOGGER.info("Loaded Fabric development mappings for mixin remapper!");
			} catch (IOException e) {
				LOGGER.error("Fabric development environment setup error - the game will probably crash soon!");
				e.printStackTrace();
			}
		}

		init(side, args, mixinLoader);
	}

	static void init(EnvType side, Map<String, String> args, MixinLoader mixinLoader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		MixinBootstrap.init();

		addConfiguration("fabric-loader.mixins.common.json");
		if (side == EnvType.CLIENT) {
			addConfiguration("fabric-loader.mixins.client.json");
		}
		if (side == EnvType.SERVER) {
			addConfiguration("fabric-loader.mixins.server.json");
		}

		mixinLoader.getCommonMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		if (side == EnvType.CLIENT) {
			mixinLoader.getClientMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		}
		if (side == EnvType.SERVER) {
			mixinLoader.getServerMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		}

		initialized = true;
	}
}
