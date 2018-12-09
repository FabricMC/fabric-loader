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

package net.fabricmc.loader.launch;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.ModInfo;

import java.util.List;

public class FabricServerTweaker extends FabricTweaker {
	@Override
	public String getLaunchTarget() {
		return "net.minecraft.server.MinecraftServer";
	}

	@Override
	public EnvType getEnvironmentType() {
		return EnvType.SERVER;
	}

	@Override
	public void getInvalidArgPrefixes(List<String> list) {
		super.getInvalidArgPrefixes(list);
		list.add("--version");
		list.add("--gameDir");
	}
}
