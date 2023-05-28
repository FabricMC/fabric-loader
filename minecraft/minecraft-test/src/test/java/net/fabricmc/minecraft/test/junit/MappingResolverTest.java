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

package net.fabricmc.minecraft.test.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

public class MappingResolverTest {
	final MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();

	@Test
	void getNamespaces() {
		assertIterableEquals(mappingResolver.getNamespaces(), List.of("named", "official", "intermediary"));
	}

	@Test
	void getCurrentRuntimeNamespace() {
		assertEquals(mappingResolver.getCurrentRuntimeNamespace(), "named");
	}

	@Test
	void mapClassName() {
		assertEquals(mappingResolver.mapClassName("intermediary", "net.minecraft.class_310"), "net.minecraft.client.MinecraftClient");
		assertEquals(mappingResolver.mapClassName("intermediary", "net.minecraft.class_310$class_5859"), "net.minecraft.client.MinecraftClient$ChatRestriction");
		assertEquals(mappingResolver.mapClassName("intermediary", "net.minecraft.Unknown"), "net.minecraft.Unknown");
	}

	@Test
	void unmapClassName() {
		assertEquals(mappingResolver.unmapClassName("intermediary", "net.minecraft.server.command.DebugPathCommand"), "net.minecraft.class_6327");
	}

	@Test
	void mapFieldName() {
		assertEquals(mappingResolver.mapFieldName("intermediary", "net.minecraft.class_2586", "field_11863", "Lnet/minecraft/class_1937;"), "world");
	}

	@Test
	void mapMethodName() {
		assertEquals(mappingResolver.mapMethodName("intermediary", "net.minecraft.class_3222", "method_14220", "()Lnet/minecraft/class_3218;"), "getWorld");
	}
}
