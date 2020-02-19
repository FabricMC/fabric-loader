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

package net.fabricmc.test.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.class_1084;
import net.minecraft.class_1293;
import net.minecraft.class_376;

@Mixin(value = class_1084.class, remap = false)
public abstract class MixinGuiMain {
	
	@Inject(at = @At("HEAD"), method="method_3730", cancellable = true)
	public void method_3730(CallbackInfoReturnable<Map<class_376, class_1293>> ci) {
		ci.setReturnValue(null);
	}
	
}
