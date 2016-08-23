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

package net.fabricmc.base.client;

import net.fabricmc.api.Side;
import net.fabricmc.base.ISidedHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

public class ClientSidedHandler implements ISidedHandler {

	@Override
	public Side getSide() {
		return Side.CLIENT;
	}

	@Override
	public EntityPlayer getClientPlayer() {
		return Minecraft.getInstance().player;
	}

	@Override
	public void runOnMainThread(Runnable runnable) {
		if (Minecraft.getInstance().isMainThread()) {
			runnable.run();
		} else {
			Minecraft.getInstance().scheduleOnMainThread(runnable);
		}
	}

	@Override
	public MinecraftServer getServerInstance() {
		return Minecraft.getInstance().server;
	}

}
