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

package net.fabricmc.loader.impl.game.minecraft.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;

class MenuHooksTest {
	@Test
	void enabledForClientsUnlessAnotherMenuOrOptOutIsPresent() {
		assertTrue(MenuHooks.isEnabled(EnvType.CLIENT, false, null));
		assertTrue(MenuHooks.isEnabled(EnvType.CLIENT, false, "true"));
		assertFalse(MenuHooks.isEnabled(EnvType.CLIENT, true, null));
		assertFalse(MenuHooks.isEnabled(EnvType.CLIENT, false, "FALSE"));
		assertFalse(MenuHooks.isEnabled(EnvType.SERVER, false, null));
	}

	@Test
	void paginationDetailsAndBackKeepTheOriginalParent() {
		List<ModContainer> mods = new ArrayList<>();

		for (int i = 0; i < 20; i++) {
			ModContainer container = mock(ModContainer.class);
			ModMetadata metadata = mock(ModMetadata.class);
			when(container.getMetadata()).thenReturn(metadata);
			when(metadata.getId()).thenReturn("mod" + i);
			when(metadata.getName()).thenReturn(String.format("Mod %02d", i));
			when(metadata.getVersion()).thenReturn(mock(Version.class));
			when(metadata.getDescription()).thenReturn("Description");
			when(metadata.getAuthors()).thenReturn(Collections.emptyList());
			mods.add(container);
		}

		Object parent = new Object();
		FakeAdapter api = new FakeAdapter();
		MenuHooks.State state = new MenuHooks.State(parent, new ModListModel(mods), null);
		Object screen = api.screen(state);
		MenuHooks.init(screen, state, api);
		assertFalse(api.button("<").active);
		api.button(">").action.run();
		assertEquals(1, state.page);
		api.buttons.clear();
		MenuHooks.init(api.opened, state, api);
		Object listScreen = api.opened;
		assertTrue(api.button("<").active);
		api.buttons.stream().filter(b -> b.label.startsWith("Mod ")).findFirst().get().action.run();
		MenuHooks.State details = api.state;
		assertEquals(listScreen, details.parent);
		MenuHooks.close(details, api);
		assertEquals(listScreen, api.opened);
		MenuHooks.close(state, api);
		assertEquals(parent, api.opened);
	}

	@Test
	void resizeClampsThePageAndLongDescriptionsKeepAllCharacters() {
		FakeAdapter api = new FakeAdapter();
		MenuHooks.State state = new MenuHooks.State(new Object(), new ModListModel(Collections.emptyList()), null);
		state.page = 99;
		MenuHooks.init(api.screen(state), state, api);
		assertEquals(0, state.page);
		assertFalse(api.button(">").active);
		ModMetadata metadata = mock(ModMetadata.class);
		Version version = mock(Version.class);
		when(metadata.getId()).thenReturn("test");
		when(metadata.getVersion()).thenReturn(version);
		when(version.getFriendlyString()).thenReturn("1");
		when(metadata.getAuthors()).thenReturn(Collections.emptyList());
		when(metadata.getDescription()).thenReturn("abcdefgh😀ijklmnop\nsecond line");
		List<String> lines = MenuHooks.details(metadata, 8);
		assertEquals("abcdefgh😀ijklmnopsecondline", String.join("", lines.subList(3, lines.size())).replace(" ", ""));
		assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= 8));
	}

	private static final class FakeAdapter implements MenuAdapter {
		final List<Button> buttons = new ArrayList<>();
		Object opened;
		MenuHooks.State state;

		@Override
		public Object text(String text) {
			return text;
		}

		@Override
		public Object screen(Object state) {
			this.state = (MenuHooks.State) state;
			return new Object();
		}

		@Override
		public Object button(int x, int y, int width, int height, String label, Runnable action) {
			return new Button(label, action);
		}

		@Override
		public void add(Object screen, Object button) {
			buttons.add((Button) button);
		}

		@Override
		public int width(Object screen) {
			return 320;
		}

		@Override
		public int height(Object screen) {
			return 180;
		}

		@Override
		public void open(Object screen) {
			opened = screen;
		}

		@Override
		public void active(Object button, boolean active) {
			((Button) button).active = active;
		}

		Button button(String label) {
			return buttons.stream().filter(b -> b.label.equals(label)).findFirst().get();
		}
	}

	private static final class Button {
		final String label;
		final Runnable action;
		boolean active = true;

		Button(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}
	}
}
