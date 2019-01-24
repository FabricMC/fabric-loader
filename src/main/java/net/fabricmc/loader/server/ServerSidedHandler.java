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

package net.fabricmc.loader.server;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.EnvironmentHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

@SuppressWarnings("deprecation")
public final class ServerSidedHandler implements EnvironmentHandler {
	private final MinecraftServer server;

	public ServerSidedHandler(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public EnvType getEnvironmentType() {
		return EnvType.SERVER;
	}

	@Override
	@Deprecated
	public PlayerEntity getClientPlayer() {
		return null;
	}

	@Override
	public void runOnMainThread(Runnable runnable) {
		server.execute(runnable);
	}

	@Override
	public MinecraftServer getServerInstance() {
		return server;
	}
}
