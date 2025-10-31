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

package net.fabricmc.loader.impl.transformer;

import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinTransform;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public final class FabricTransformer {
	public static byte[] transform(boolean isDevelopment, EnvType envType, String name, byte[] bytes) {
		Set<BuiltinTransform> transforms = FabricLoaderImpl.INSTANCE.getGameProvider().getBuiltinTransforms(name);
		boolean transformAccess = transforms.contains(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS) && FabricLauncherBase.getLauncher().getMappingConfiguration().requiresPackageAccessHack();
		boolean environmentStrip = transforms.contains(BuiltinTransform.STRIP_ENVIRONMENT);
		boolean applyClassTweaker = transforms.contains(BuiltinTransform.CLASS_TWEAKS) && FabricLoaderImpl.INSTANCE.getClassTweaker().getTargets().contains(name.replace('.', '/'));

		if (!transformAccess && !environmentStrip && !applyClassTweaker) {
			return bytes;
		}

		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = new ClassWriter(classReader, 0);
		ClassVisitor visitor = classWriter;
		int visitorCount = 0;

		if (applyClassTweaker) {
			visitor = FabricLoaderImpl.INSTANCE.getClassTweaker().createClassVisitor(FabricLoaderImpl.ASM_VERSION, visitor, null); // TODO: generated classes?
			visitorCount++;
		}

		if (transformAccess) {
			visitor = new PackageAccessFixer(FabricLoaderImpl.ASM_VERSION, visitor);
			visitorCount++;
		}

		if (environmentStrip) {
			EnvironmentStrippingData stripData = new EnvironmentStrippingData(FabricLoaderImpl.ASM_VERSION, envType.toString());
			classReader.accept(stripData, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			if (stripData.stripEntireClass()) {
				throw new RuntimeException("Cannot load class " + name + " in environment type " + envType);
			}

			if (!stripData.isEmpty()) {
				visitor = new ClassStripper(FabricLoaderImpl.ASM_VERSION, visitor, stripData.getStripInterfaces(), stripData.getStripFields(), stripData.getStripMethods());
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
