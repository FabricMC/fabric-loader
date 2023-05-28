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

package net.fabricmc.loader.impl.util.mappings;

import org.spongepowered.asm.mixin.extensibility.IRemapper;

import net.fabricmc.mappingio.tree.MappingTree;

public class MixinRemapper implements IRemapper {
	protected final MappingTree mappings;
	protected final int fromId;
	protected final int toId;

	public MixinRemapper(MappingTree mappings, int fromId, int toId) {
		this.mappings = mappings;
		this.fromId = fromId;
		this.toId = toId;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		final MappingTree.MethodMapping method = mappings.getMethod(owner, name, desc, fromId);
		return method == null ? name : method.getName(toId);
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		final MappingTree.FieldMapping field = mappings.getField(owner, name, desc, fromId);
		return field == null ? name : field.getName(toId);
	}

	@Override
	public String map(String typeName) {
		return mappings.mapClassName(typeName, fromId, toId);
	}

	@Override
	public String unmap(String typeName) {
		return mappings.mapClassName(typeName, toId, fromId);
	}

	@Override
	public String mapDesc(String desc) {
		return mappings.mapDesc(desc, fromId, toId);
	}

	@Override
	public String unmapDesc(String desc) {
		return mappings.mapDesc(desc, toId, fromId);
	}
}
