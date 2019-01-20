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

package net.fabricmc.loader.util;

import net.fabricmc.mappings.*;
import net.fabricmc.mappings.helpers.mixin.MixinMappingsRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

import java.util.HashMap;
import java.util.Map;

public class MixinIntermediaryDevRemapper extends MixinMappingsRemapper {
	public MixinIntermediaryDevRemapper(Mappings mappings, String from, String to) {
		super(mappings, from, to);
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		String result = super.mapMethodName(owner, name, desc);
		if (result.equals(name)) {
			String otherClass = unmap(owner);
			return super.mapMethodName(otherClass, name, unmapDesc(desc));
		} else {
			return result;
		}
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		String result = super.mapFieldName(owner, name, desc);
		if (result.equals(name)) {
			String otherClass = unmap(owner);
			return super.mapFieldName(otherClass, name, unmapDesc(desc));
		} else {
			return result;
		}
	}
}
