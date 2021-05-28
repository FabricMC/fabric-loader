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

package net.fabricmc.loader.transformer;

import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.game.MinecraftGameProvider;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public final class FabricTransformer {
	public static byte[] lwTransformerHook(String name, String transformedName, byte[] bytes) {
		boolean isDevelopment = FabricLauncherBase.getLauncher().isDevelopment();
		EnvType envType = FabricLauncherBase.getLauncher().getEnvironmentType();

		byte[] input = MinecraftGameProvider.TRANSFORMER.transform(name);
		if (input != null) {
			return FabricTransformer.transform(isDevelopment, envType, name, input);
		} else {
			if (bytes != null) {
				return FabricTransformer.transform(isDevelopment, envType, name, bytes);
			} else {
				return null;
			}
		}

	}

	public static byte[] transform(boolean isDevelopment, EnvType envType, String name, byte[] bytes) {
		boolean isMinecraftClass = name.startsWith("net.minecraft.") || name.indexOf('.') < 0;
		boolean transformAccess = isMinecraftClass && FabricLauncherBase.getLauncher().getMappingConfiguration().requiresPackageAccessHack();
		boolean environmentStrip = !isMinecraftClass || isDevelopment;
		boolean applyAccessWidener = isMinecraftClass && FabricLoader.INSTANCE.getAccessWidener().getTargets().contains(name);

		if (!transformAccess && !environmentStrip && !applyAccessWidener) {
			return bytes;
		}

		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = new ClassWriter(0);
		ClassVisitor visitor = classWriter;
		int visitorCount = 0;

		if (applyAccessWidener) {
			visitor = AccessWidenerVisitor.createClassVisitor(FabricLoader.ASM_VERSION, visitor, FabricLoader.INSTANCE.getAccessWidener());
			visitorCount++;
		}

		if (transformAccess) {
			visitor = new PackageAccessFixer(FabricLoader.ASM_VERSION, visitor);
			visitorCount++;
		}

		if (environmentStrip) {
			EnvironmentStrippingData stripData = new EnvironmentStrippingData(FabricLoader.ASM_VERSION, envType.toString());
			classReader.accept(stripData, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			if (stripData.stripEntireClass()) {
				throw new RuntimeException("Cannot load class " + name + " in environment type " + envType);
			}
			if (!stripData.isEmpty()) {
				visitor = new ClassStripper(FabricLoader.ASM_VERSION, visitor, stripData.getStripInterfaces(), stripData.getStripFields(), stripData.getStripMethods());
				visitorCount++;
			}
		}

		if (visitorCount <= 0) {
			return bytes;
		}

		classReader.accept(visitor, 0);
		return classWriter.toByteArray();
	}
}
