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

package net.fabricmc.loader.impl.game.minecraft;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;

public final class Hooks {
	public static final String INTERNAL_NAME = Hooks.class.getName().replace('.', '/');

	public static String appletMainClass;

	public static final String FABRIC = "fabric";
	public static final String VANILLA = "vanilla";

	private static final Logger LOGGER = LogManager.getLogger("Fabric|Branding");

	public static String insertBranding(final String brand) {
		if (brand == null || brand.isEmpty()) {
			LOGGER.warn("Null or empty branding found!", new IllegalStateException());
			return FABRIC;
		}

		return VANILLA.equals(brand) ? FABRIC : brand + ',' + FABRIC;
	}

	public static void startClient(File runDir, Object gameInstance) {
		if (runDir == null) {
			runDir = new File(".");
		}

		FabricLoaderImpl.INSTANCE.prepareModInit(runDir.toPath(), gameInstance);
		EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
		EntrypointUtils.invoke("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
	}

	public static void startServer(File runDir, Object gameInstance) {
		if (runDir == null) {
			runDir = new File(".");
		}

		FabricLoaderImpl.INSTANCE.prepareModInit(runDir.toPath(), gameInstance);
		EntrypointUtils.invoke("main", ModInitializer.class, ModInitializer::onInitialize);
		EntrypointUtils.invoke("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
	}

	public static void setGameInstance(Object gameInstance) {
		FabricLoaderImpl.INSTANCE.setGameInstance(gameInstance);
	}
}
