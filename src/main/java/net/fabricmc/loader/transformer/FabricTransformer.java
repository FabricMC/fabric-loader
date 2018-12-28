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

import net.fabricmc.api.EnvType;
import org.objectweb.asm.*;

public final class FabricTransformer {
	public static byte[] transform(boolean isDevelopment, EnvType envType, String name, byte[] bytes) {
		boolean isMinecraftClass = name.startsWith("net.minecraft") || name.indexOf('.') < 0;
		boolean transformAccess = isDevelopment && isMinecraftClass;
		boolean environmentStrip = !isMinecraftClass || isDevelopment;
		if (!transformAccess && !environmentStrip) {
			return bytes;
		}

		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = new ClassWriter(0);
		ClassVisitor visitor = classWriter;
		if (transformAccess) {
			visitor = new PackageAccessFixer(Opcodes.ASM7, visitor);
		}
		if (environmentStrip) {
			EnvironmentStripData stripData = new EnvironmentStripData(Opcodes.ASM7, envType.toString());
			classReader.accept(stripData, 0);
			if (stripData.stripEntireClass()) {
				throw new RuntimeException("Cannot load class " + name + " in environment type " + envType);
			}
			if (!stripData.isEmpty()) {
				visitor = new ClassStripper(Opcodes.ASM7, visitor, stripData.getStripInterfaces(), stripData.getStripFields(), stripData.getStripMethods());
			}
		}
		classReader.accept(visitor, 0);
		return classWriter.toByteArray();
	}
}
