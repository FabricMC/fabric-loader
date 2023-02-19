package net.fabricmc.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.impl.util.Localization;

public class LocalizationTests {
	@Test
	public void formatRoot() {
		Assertions.assertEquals("client", Localization.formatRoot("environment.client"));
		Assertions.assertEquals("Install A, B.", Localization.formatRoot("resolution.solution.addMod", "A", "B"));
	}
}
