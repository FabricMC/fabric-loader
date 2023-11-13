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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.fabricmc.loader.api.FabricLoader;

public class JunitTest {
	@BeforeAll
	public static void setup() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	public void testItems() {
		Identifier id = Registries.ITEM.getId(Items.DIAMOND);
		assertEquals(id.toString(), "minecraft:diamond");

		System.out.println(id);
	}

	@Test
	public void testMixin() {
		// MixinGrassBlock sets canGrow to false
		GrassBlock grassBlock = (GrassBlock) Blocks.GRASS_BLOCK;
		boolean canGrow = grassBlock.canGrow(null, null, null, null);
		assertFalse(canGrow);
	}

	@Test
	public void testMixinExtras() {
		// MixinGrassBlock sets isFertilizable to true
		GrassBlock grassBlock = (GrassBlock) Blocks.GRASS_BLOCK;
		boolean isFertilizable = grassBlock.isFertilizable(null, BlockPos.ORIGIN, null);
		assertTrue(isFertilizable);
	}

	@Test
	public void testAccessLoader() {
		FabricLoader.getInstance().getAllMods();
	}
}
