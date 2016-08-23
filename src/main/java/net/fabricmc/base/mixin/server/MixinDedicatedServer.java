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

package net.fabricmc.base.mixin.server;

import net.fabricmc.base.Fabric;
import net.fabricmc.base.ISidedHandler;
import net.fabricmc.base.server.ServerSidedHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DedicatedServer.class, remap = false)
public class MixinDedicatedServer {

	@Inject(method = "<init>*", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		ISidedHandler handler = Fabric.getSidedHandler();
		if (handler instanceof ServerSidedHandler) {
			((ServerSidedHandler)handler).setServerInstance((MinecraftServer)(Object)this);
		}
	}

}
