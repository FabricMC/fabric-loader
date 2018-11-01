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

package net.fabricmc.loader.mixin.client;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.client.ClientSidedHandler;
import net.minecraft.client.MinecraftGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(value = MinecraftGame.class)
public class MixinMinecraftGame {

	@Inject(method = "init()V", at = @At("HEAD"))
	public void init(CallbackInfo info) {
		MinecraftGame game = ((MinecraftGame) (Object) this);
		FabricLoader.INSTANCE.initialize(game.runDirectory, new ClientSidedHandler());
		FabricLoader.INSTANCE.load(new File(game.runDirectory, "mods"));
	}

}
