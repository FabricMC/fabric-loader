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
import net.fabricmc.loader.ModContainer;
import net.fabricmc.loader.ModInfo;
import net.fabricmc.mappings.Mappings;
import net.fabricmc.mappings.helpers.mixin.MixinMappingsRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() {

	}

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|MixinBootstrap");
	private static boolean initialized = false;

	static void addConfiguration(String configuration) {
		Mixins.addConfiguration(configuration);
	}

	static Set<String> getClientMixinConfigs(FabricLoader loader) {
		return loader.getModContainers().stream()
			.map(ModContainer::getInfo)
			.map(ModInfo::getMixins)
			.flatMap(info -> Stream.of(info.getClient()))
			.filter(s -> s != null && !s.isEmpty())
			.collect(Collectors.toSet());
	}

	static Set<String> getCommonMixinConfigs(FabricLoader loader) {
		return loader.getModContainers().stream()
			.map(ModContainer::getInfo)
			.map(ModInfo::getMixins)
			.flatMap(info -> Stream.of(info.getCommon()))
			.filter(s -> s != null && !s.isEmpty())
			.collect(Collectors.toSet());
	}

	static Set<String> getServerMixinConfigs(FabricLoader loader) {
		return loader.getModContainers().stream()
			.map(ModContainer::getInfo)
			.map(ModInfo::getMixins)
			.flatMap(info -> Stream.of(info.getServer()))
			.filter(s -> s != null && !s.isEmpty())
			.collect(Collectors.toSet());
	}

	public static void init(EnvType side, Map<String, String> args, FabricLoader loader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			Mappings mappings = FabricLauncherBase.getLauncher().getMappings();
			if (mappings != null && mappings.getNamespaces().contains("intermediary") && mappings.getNamespaces().contains("named")) {
				try {
					MixinMappingsRemapper remapper = new MixinMappingsRemapper(mappings, "intermediary", "named");
					MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
					LOGGER.info("Loaded Fabric development mappings for mixin remapper!");
				} catch (Exception e) {
					LOGGER.error("Fabric development environment setup error - the game will probably crash soon!");
					e.printStackTrace();
				}
			}
		}

		MixinBootstrap.init();

		addConfiguration("fabric-loader.mixins.common.json");
		if (side == EnvType.CLIENT) {
			addConfiguration("fabric-loader.mixins.client.json");
		}
		if (side == EnvType.SERVER) {
			addConfiguration("fabric-loader.mixins.server.json");
		}

		getCommonMixinConfigs(loader).forEach(FabricMixinBootstrap::addConfiguration);
		if (side == EnvType.CLIENT) {
			getClientMixinConfigs(loader).forEach(FabricMixinBootstrap::addConfiguration);
		}
		if (side == EnvType.SERVER) {
			getServerMixinConfigs(loader).forEach(FabricMixinBootstrap::addConfiguration);
		}

		initialized = true;
	}
}
