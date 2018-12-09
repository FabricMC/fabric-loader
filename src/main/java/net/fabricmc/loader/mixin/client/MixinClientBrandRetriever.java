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

import net.minecraft.client.ClientBrandRetriever;
import org.lwjgl.system.CallbackI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientBrandRetriever.class, remap = false)
public abstract class MixinClientBrandRetriever {
	@Inject(at = @At("RETURN"), method = "getClientModName", cancellable = true)
	private void getClientModName(CallbackInfoReturnable<String> info) {
		if (info.getReturnValue().equals("vanilla")) {
			info.setReturnValue("Fabric");
		} else {
			info.setReturnValue(info.getReturnValue() + "+Fabric");
		}
	}
}
