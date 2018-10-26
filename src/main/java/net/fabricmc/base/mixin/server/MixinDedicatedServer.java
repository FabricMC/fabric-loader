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
import com.mojang.datafixers.DataFixer;
import net.fabricmc.base.Fabric;
import net.fabricmc.base.loader.Loader;
import net.fabricmc.base.server.ServerSidedHandler;
import net.minecraft.command.CommandManagerServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.UserCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;

@Mixin(DedicatedServer.class)
public abstract class MixinDedicatedServer extends MinecraftServer {
	public MixinDedicatedServer(
		@Nullable
			File aFile1,
		Proxy aProxy2,
		DataFixer aDataFixer3,
		CommandManagerServer aCommandManagerServer4,
		YggdrasilAuthenticationService aYggdrasilAuthenticationService5,
		MinecraftSessionService aMinecraftSessionService6,
		GameProfileRepository aGameProfileRepository7,
		UserCache aUserCache8) {
		super(aFile1, aProxy2, aDataFixer3, aCommandManagerServer4, aYggdrasilAuthenticationService5, aMinecraftSessionService6, aGameProfileRepository7, aUserCache8);
	}

	@Inject(method = "setupServer", at = @At("HEAD"))
	public void j(CallbackInfoReturnable<Boolean> info) throws IOException {
		Fabric.initialize(this.getFile(""), new ServerSidedHandler(this));
		Loader.INSTANCE.load(this.getFile("mods"));
	}
}
