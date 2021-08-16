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

package net.fabricmc.loader.impl.launch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;
import net.fabricmc.mapping.tree.TinyTree;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() { }

	private static boolean initialized = false;

	public static void init(EnvType side, FabricLoaderImpl loader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration mappingConfiguration = FabricLauncherBase.getLauncher().getMappingConfiguration();
			TinyTree mappings = mappingConfiguration.getMappings();

			if (mappings != null) {
				List<String> namespaces = mappings.getMetadata().getNamespaces();

				if (namespaces.contains("intermediary") && namespaces.contains(mappingConfiguration.getTargetNamespace())) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, "intermediary", mappingConfiguration.getTargetNamespace());
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						Log.info(LogCategory.MIXIN, "Loaded Fabric development mappings for mixin remapper!");
					} catch (Exception e) {
						Log.error(LogCategory.MIXIN, "Fabric development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				}
			}
		}

		MixinBootstrap.init();

		Map<String, ModContainerImpl> configToModMap = new HashMap<>();

		for (ModContainerImpl mod : loader.getModsInternal()) {
			for (String config : mod.getMetadata().getMixinConfigs(side)) {
				ModContainerImpl prev = configToModMap.putIfAbsent(config, mod);
				if (prev != null) throw new RuntimeException(String.format("Non-unique mixin config name %s used by %s and %s", config, prev.getMetadata().getId(), mod.getMetadata().getId()));

				Mixins.addConfiguration(config);
			}
		}

		for (Config config : Mixins.getConfigs()) {
			ModContainerImpl mod = configToModMap.get(config.getName());
			if (mod == null) continue;
		}

		try {
			IMixinConfig.class.getMethod("decorate", String.class, Object.class);
			MixinConfigDecorator.apply(configToModMap);
		} catch (NoSuchMethodException e) {
			Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
		}

		initialized = true;
	}

	private static final class MixinConfigDecorator {
		private static final SemanticVersion LAST_LOADER_MIXIN_0_9_2 = new SemanticVersionImpl(new int[] { 0, 11, 9999 }, null, null);

		static void apply(Map<String, ModContainerImpl> configToModMap) {
			for (Config rawConfig : Mixins.getConfigs()) {
				ModContainerImpl mod = configToModMap.get(rawConfig.getName());
				if (mod == null) continue;

				IMixinConfig config = rawConfig.getConfig();
				config.decorate(FabricUtil.KEY_MOD_ID, mod.getMetadata().getId());
				config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod));
			}
		}

		private static int getMixinCompat(ModContainerImpl mod) {
			Manifest manifest = FabricLauncherBase.getLauncher().getManifest(mod.getOriginPath());
			String versionStr;

			if (manifest != null
					&& "net.fabricmc".equals(ManifestUtil.getManifestValue(manifest, ManifestUtil.NAME_MIXIN_GROUP))
					&& (versionStr = ManifestUtil.getManifestValue(manifest, ManifestUtil.NAME_MIXIN_VERSION)) != null) {
				try {
					SemanticVersion version = SemanticVersion.parse(versionStr);
					int major = version.getVersionComponent(0);
					int minor = Math.min(version.getVersionComponent(1), 999);
					int patch = Math.min(version.getVersionComponent(2), 999);

					return (major * 1000 + minor) * 1000 + patch;
				} catch (VersionParsingException e) {
					Log.warn(LogCategory.GENERAL, "Error parsing Mixin Version from Manifest for %s", mod, e);
				}
			}

			// no manifest record, try to infer from loader dependency being >= 0.12.x (first with fabrix-mixin 0.9.4+)

			for (ModDependency dep : mod.getMetadata().getDependencies()) {
				if (dep.getKind() == Kind.DEPENDS && (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader"))) {
					if (!dep.matches(LAST_LOADER_MIXIN_0_9_2)) { // not satisfied by a loader version with the old mixin version
						return FabricUtil.COMPATIBILITY_0_9_4;
					}
				}
			}

			// default to old fabric-mixin

			return FabricUtil.COMPATIBILITY_0_9_2;
		}
	}
}
