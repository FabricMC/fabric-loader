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

package net.fabricmc.loader.game;

import net.fabricmc.api.EnvType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BukkitMinecraftGameProvider extends MinecraftGameProvider {
	@Override
	public String getGameId() {
		return super.getGameId() + "-bukkit";
	}

	@Override
	public String getGameName() {
		return super.getGameName() + "/Bukkit";
	}

	@Override
	public void gatherGameContextMappingSteps(List<MappingStep> steps) {
		if (versionData == null || versionData.name == null) {
			throw new RuntimeException("Unknown game version! Cannot proceed.");
		}

		steps.add(
			new MappingStep(
				"bukkit", "official",
				gameJar.getParent().resolve("bukkit-" + versionData.name + ".tiny")
			)
		);

		super.gatherGameContextMappingSteps(steps);
	}

	@Override
	protected Optional<GameProviderHelper.EntrypointResult> findEntrypoint(ClassLoader loader) {
		if (envType != EnvType.SERVER) {
			return Optional.empty();
		}

		return GameProviderHelper.findFirstClass(loader, Collections.singletonList("org.bukkit.craftbukkit.Main"));
	}

	@Override
	public String getSourceMappingNamespace() {
		return "bukkit";
	}
}
