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

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import net.fabricmc.api.Side;
import net.fabricmc.loader.util.mixin.MixinIntermediaryDevRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.*;
import java.util.Arrays;
import java.util.List;
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

	static void init(Side side, Map<String, String> args, MixinLoader mixinLoader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
			String mappingFileName = args.get("--fabricMappingFile");
			if (mappingFileName != null) {
				File mappingFile = new File(mappingFileName);
				if (mappingFile.exists()) {
					try (InputStream mappingStream = new FileInputStream(mappingFile)) {
						InputStream targetStream = mappingStream;

						if (mappingFileName.endsWith(".gz")) {
							targetStream = new GZIPInputStream(mappingStream);
						}

						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper();
						remapper.readMapping(new BufferedReader(new InputStreamReader(targetStream)), "intermediary", "pomf");
						mappingStream.close();

						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);

						LOGGER.info("Loaded Fabric development mappings for mixin remapper!");
					} catch (IOException e) {
						LOGGER.error("Fabric development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				} else {
					LOGGER.error("Fabric development mappings not found despite being specified - the game will probably crash soon!");
				}
			}
		}

		MixinBootstrap.init();

		addConfiguration("fabricmc.mixins.common.json");
		if (side.hasClient()) {
			addConfiguration("fabricmc.mixins.client.json");
		}
		if (side.hasServer()) {
			addConfiguration("fabricmc.mixins.server.json");
		}

		mixinLoader.getCommonMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		if (side.hasClient()) {
			mixinLoader.getClientMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		}
		if (side.hasServer()) {
			mixinLoader.getServerMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
		}

		initialized = true;
	}
}
