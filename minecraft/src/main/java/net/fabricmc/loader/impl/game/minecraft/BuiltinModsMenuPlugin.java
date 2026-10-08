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

package net.fabricmc.loader.impl.game.minecraft;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Selects the version-specific menu without loading its Minecraft classes on other versions. */
public final class BuiltinModsMenuPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		FabricLoader loader = FabricLoader.getInstance();
		return isEnabled(loader.getEnvironmentType(), loader.getModContainer("minecraft")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(""),
				loader.isModLoaded("modmenu"), System.getProperty("fabric.modsMenu", "true"));
	}

	static boolean isEnabled(EnvType environment, String minecraftVersion, boolean modMenuLoaded, String setting) {
		return environment == EnvType.CLIENT && "1.20.2".equals(minecraftVersion) && !modMenuLoaded && !"false".equalsIgnoreCase(setting);
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return shouldApplyMixin(null, null) ? Collections.singletonList("TitleScreenMixin") : Collections.emptyList();
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
