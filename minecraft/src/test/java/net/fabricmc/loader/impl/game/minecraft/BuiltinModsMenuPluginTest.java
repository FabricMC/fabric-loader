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

package net.fabricmc.loader.impl.game.minecraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.fabricmc.api.EnvType;

class BuiltinModsMenuPluginTest {
	@Test
	void onlySupportedReleaseIsEnabled() {
		assertTrue(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "1.20.2", false, "true"));
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "1.20.1", false, "true"));
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "1.21", false, "true"));
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "23w40a", false, "true"));
	}

	@Test
	void dedicatedServerNeverSelectsClientMixins() {
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.SERVER, "1.20.2", false, "true"));
	}

	@Test
	void existingMenuAndUserOptOutTakePriority() {
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "1.20.2", true, "true"));
		assertFalse(BuiltinModsMenuPlugin.isEnabled(EnvType.CLIENT, "1.20.2", false, "FALSE"));
	}
}
