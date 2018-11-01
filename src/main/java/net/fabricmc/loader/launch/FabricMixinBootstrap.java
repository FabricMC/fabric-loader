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
import net.fabricmc.loader.util.mixin.MixinTinyRemapper;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() {

	}

	static final String APPLIED_MIXIN_CONFIGS_FILENAME = ".fabric-applied-mixin-configs";
	static final String MAPPINGS_FILENAME = ".fabric-dev-mappings.tiny";
	private static List<String> appliedMixinConfigs;
	private static boolean initialized = false;
	private static File mappingFile;

	static File getMappingFile() {
		return mappingFile;
	}

	static void setMappingFile(File value) {
		mappingFile = value;
	}

	public static void addConfiguration(String configuration) {
		if (appliedMixinConfigs == null || !appliedMixinConfigs.contains(configuration)) {
			Mixins.addConfiguration(configuration);
		}
	}

	static void init(Side side, MixinLoader mixinLoader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		InputStream appliedMixinsStream = FabricMixinBootstrap.class.getClassLoader().getResourceAsStream(APPLIED_MIXIN_CONFIGS_FILENAME);
		if (appliedMixinsStream != null) {
			try {
				byte[] data = ByteStreams.toByteArray(appliedMixinsStream);
				appliedMixinConfigs = Arrays.asList(new String(data, Charsets.UTF_8).split("\n"));
				appliedMixinsStream.close();
			} catch (IOException e) {
				System.err.println("Fabric development environment setup error - the game will probably crash soon!");
				e.printStackTrace();
			}
		}

		try {
			InputStream mappingStream = mappingFile != null
			                            ? new FileInputStream(mappingFile)
			                            : FabricMixinBootstrap.class.getClassLoader().getResourceAsStream(MAPPINGS_FILENAME);

			if (mappingStream != null) {
				try {
					MixinTinyRemapper remapper = new MixinTinyRemapper();
					remapper.readMapping(new BufferedReader(new InputStreamReader(mappingStream)), "intermediary", "pomf");
					mappingStream.close();

					MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
				} catch (IOException e) {
					System.err.println("Fabric development environment setup error - the game will probably crash soon!");
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			// Ignore
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

	static List<String> getAppliedMixinConfigs() {
		return appliedMixinConfigs;
	}
}
