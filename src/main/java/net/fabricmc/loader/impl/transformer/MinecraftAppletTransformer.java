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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import net.fabricmc.loader.impl.FabricLoaderImpl;

public class MinecraftAppletTransformer extends ClassRemapper {
	private static final Remapper REMAPPER = new SimpleRemapper(getRenames());

	private static Map<String, String> getRenames() {
		Map<String, String> renames = new HashMap<>();

		// TODO move this transformer into the minecraft specific code.
		renames.put("java/applet/Applet", "net/fabricmc/loader/impl/game/minecraft/applet/stub/Applet");
		renames.put("java/applet/AppletStub", "net/fabricmc/loader/impl/game/minecraft/applet/stub/AppletStub");

		return Collections.unmodifiableMap(renames);
	}

	public MinecraftAppletTransformer(ClassVisitor classVisitor) {
		super(FabricLoaderImpl.ASM_VERSION, classVisitor, REMAPPER);
	}
}
