package net.fabricmc.minecraft.test.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.Bootstrap;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JunitTest {
	@BeforeAll
	public static void setup() {
		Bootstrap.initialize();
	}

	@Test
	public void testItems() {
		Identifier id = Registries.ITEM.getId(Items.DIAMOND);
		assertEquals(id.toString(), "hello");
	}

	@Test
	public void testMixin() {
		// TODO write a test that depends on a mixin
	}
}
