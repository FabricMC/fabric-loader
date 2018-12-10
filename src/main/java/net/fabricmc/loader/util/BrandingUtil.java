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

package net.fabricmc.loader.util;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class BrandingUtil {
	public static final String FABRIC = "fabric";
	public static final String VANILLA = "vanilla";

	private BrandingUtil() {
	}

	public static void brand(final CallbackInfoReturnable<String> cir) {
		if (cir.getReturnValue().equals(VANILLA)) {
			cir.setReturnValue(FABRIC);
		} else {
			cir.setReturnValue(cir.getReturnValue() + "," + FABRIC);
		}
	}
}
