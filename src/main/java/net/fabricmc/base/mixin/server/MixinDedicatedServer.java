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

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.fabricmc.base.Fabric;
import net.fabricmc.base.loader.Loader;
import net.fabricmc.base.server.ServerSidedHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.UserCache;
import net.minecraft.server.dedicated.DedicatedServer;
import none.pf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;

@Mixin(value = DedicatedServer.class, remap = false)
public abstract class MixinDedicatedServer extends MinecraftServer {

	public MixinDedicatedServer(File a1, Proxy a2, pf a3, YggdrasilAuthenticationService a4, MinecraftSessionService a5, GameProfileRepository a6, UserCache a7) {
		super(a1, a2, a3, a4, a5, a6, a7);
	}

	@Inject(method = "j", at = @At("HEAD"))
	public void j(CallbackInfoReturnable<Boolean> info) throws IOException {
		Fabric.initialize(this.n, new ServerSidedHandler(this));
		Loader.INSTANCE.load(new File(this.n, "mods"));
	}

}
