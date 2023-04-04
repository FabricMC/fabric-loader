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

package net.fabricmc.loader.impl.launch.knot;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public class MixinServiceKnotBootstrap implements IMixinServiceBootstrap {
	public MixinServiceKnotBootstrap() {
		if (!FabricLauncherBase.getLauncher().useFabricMixinServices()) {
			// If an exception is thrown here, Mixin is designed to skip over this service.
			// This also happens with its bundled LaunchWrapper and ModLauncher implementations.
			throw new UnsupportedOperationException("MixinServiceKnotBootstrap is not supported on this launch platform.");
		}
	}

	@Override
	public String getName() {
		return "Knot";
	}

	@Override
	public String getServiceClassName() {
		return "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot";
	}

	@Override
	public void bootstrap() {
		// already done in Knot
	}
}
