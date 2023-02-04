package net.fabricmc.minecraft.test.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
	}

	@Test
	public void testMixin() {
		// MixinGrassBlock sets canGrow to false
		GrassBlock grassBlock = (GrassBlock) Blocks.GRASS_BLOCK;
		boolean canGrow = grassBlock.canGrow(null, null, null, null);
		assertFalse(canGrow);
	}
}
