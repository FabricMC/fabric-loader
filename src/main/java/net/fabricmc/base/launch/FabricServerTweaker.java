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

package net.fabricmc.base.launch;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

public class FabricServerTweaker extends FabricTweaker {
	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		super.injectIntoClassLoader(launchClassLoader);
		addMixinConfiguration("fabricmc.mixins.server.json");
		mixinLoader.getServerMixinConfigs().forEach(this::addMixinConfiguration);
		MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
	}

	@Override
	public String getLaunchTarget() {
		return "net.minecraft.server.MinecraftServer";
	}
}
