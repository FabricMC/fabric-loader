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

package net.fabricmc.loader;

import net.fabricmc.api.EnvType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ThreadTaskQueue;

/**
 * A EnvironmentHandler provides certain crucial information about the
 * environment of the game, which can be the client or the server.
 *
 * @see FabricLoader#getEnvironmentHandler()
 */
public interface EnvironmentHandler {
	/**
	 * @return The environment type the game is currently in.
	 */
	EnvType getEnvironmentType();

	/**
	 * @deprecated Will be removed in 0.4.0.
	 */
	@Deprecated
	PlayerEntity getClientPlayer();

	/**
	 * @deprecated Will be removed in 0.4.0.
	 */
	@Deprecated
	void runOnMainThread(Runnable runnable);

	/**
	 * @return The server instance for this environment.
	 *         It can be integrated or dedicated.
	 */
	MinecraftServer getServerInstance();
}
