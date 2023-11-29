package net.fabricmc.minecraft.test.smoke;

import net.fabricmc.api.ModInitializer;

// For now just tests we get this far
public class TestEntrypoint implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("Fabric smoke test mod initialized!");
		System.exit(0);
	}
}
