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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ContactInformation;

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
	void selectingAndScrollingStayOnTheSameScreen() {
		List<ModContainer> mods = new ArrayList<>();

		for (int i = 0; i < 20; i++) {
			ModContainer container = mock(ModContainer.class);
			ModMetadata metadata = mock(ModMetadata.class);
			when(container.getMetadata()).thenReturn(metadata);
			when(metadata.getId()).thenReturn("mod" + i);
			when(metadata.getName()).thenReturn(String.format("Mod %02d", i));
			mods.add(container);
		}

		Object parent = new Object();
		FakeAdapter api = new FakeAdapter();
		MenuHooks.State state = new MenuHooks.State(parent, new ModListModel(mods), null);
		MenuHooks.init(api.screen(state), state, api);
		assertTrue(MenuHooks.input(state, api, "mouseClicked", new Object[] {10.0, 110.0, 0}));
		assertEquals("mod1", state.mod.getId());
		assertEquals(null, api.opened);
		assertTrue(MenuHooks.input(state, api, "mouseScrolled", new Object[] {10.0, 110.0, -3.0}));
		assertEquals(54, state.listScroll);
		state.refresh("Mod 19");
		assertEquals(1, state.mods.size());
		assertEquals("mod19", state.mod.getId());
		assertEquals(0, state.listScroll);
		MenuHooks.close(state, api);
		assertEquals(parent, api.opened);
	}

	@Test
	void wrappingPreservesUnicodeAndParagraphs() {
		List<String> lines = MenuHooks.wrap(MenuCanvas.EMPTY, "abcdefgh😀ijklmnop\nsecond line", 48);
		assertEquals("abcdefgh😀ijklmnopsecondline", String.join("", lines).replace(" ", ""));
		assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= 8));
	}

	@Test
	void detailsRenderBesideTheListAndSearchClearsSelection() {
		ModContainer container = mock(ModContainer.class);
		ModMetadata mod = mock(ModMetadata.class);
		Version version = mock(Version.class);
		when(container.getMetadata()).thenReturn(mod);
		when(mod.getId()).thenReturn("example");
		when(mod.getName()).thenReturn("Example Mod");
		when(mod.getDescription()).thenReturn("A sample description");
		when(mod.getVersion()).thenReturn(version);
		when(version.getFriendlyString()).thenReturn("1.2.3");
		when(mod.getAuthors()).thenReturn(Collections.emptyList());
		when(mod.getLicense()).thenReturn(Collections.singleton("MIT"));
		ContactInformation contact = mock(ContactInformation.class);
		Map<String, String> links = new LinkedHashMap<>();
		links.put("homepage", "https://example.org");
		links.put("issues", "https://example.org/issues");
		when(mod.getContact()).thenReturn(contact);
		when(contact.asMap()).thenReturn(links);
		when(contact.get("homepage")).thenReturn(Optional.of(links.get("homepage")));
		when(contact.get("issues")).thenReturn(Optional.of(links.get("issues")));
		FakeAdapter api = new FakeAdapter();
		MenuHooks.State state = new MenuHooks.State(new Object(), new ModListModel(Collections.singleton(container)), null);
		Object screen = api.screen(state);
		MenuHooks.init(screen, state, api);
		MenuHooks.render(screen, state, api, null, 10, 110, 0);
		assertTrue(MenuHooks.input(state, api, "mouseScrolled", new Object[] { -1.0 }));
		assertTrue(api.drawn.stream().anyMatch(line -> line.startsWith("Example Mod@44,")));
		assertTrue(api.drawn.stream().anyMatch(line -> line.startsWith("Example Mod@" + (state.right + 36) + ",")));
		assertTrue(api.drawn.stream().anyMatch(line -> line.startsWith("1.2.3@")));
		assertTrue(api.drawn.stream().anyMatch(line -> line.startsWith("License@")));
		api.button("Website").action.run();
		assertEquals("https://example.org", api.openedLink);
		api.button("Issues").action.run();
		assertEquals("https://example.org/issues", api.openedLink);
		api.button("Open Mods Folder").action.run();
		assertTrue(api.folderOpened);
		String homepage = api.drawn.stream().filter(line -> line.startsWith("Homepage@")).findFirst().get();
		int y = Integer.parseInt(homepage.substring(homepage.indexOf(',') + 1));
		assertTrue(MenuHooks.input(state, api, "mouseClicked", new Object[] { (double) state.right + 5, (double) y + 1, 0 }));
		assertEquals("https://example.org", api.openedLink);
		state.refresh("absent");
		MenuHooks.render(screen, state, api, null, 0, 0, 0);
		assertEquals(null, state.mod);
		assertTrue(api.drawn.stream().anyMatch(line -> line.startsWith("No matching mods@")));
	}

	private static final class FakeAdapter implements MenuAdapter {
		final List<Button> buttons = new ArrayList<>();
		final List<String> drawn = new ArrayList<>();
		String openedLink;
		boolean folderOpened;

		@Override
		public void openLink(String address) {
			openedLink = address;
		}

		@Override
		public void openModsFolder() {
			folderOpened = true;
		}

		@Override
		public MenuCanvas canvas(Object screen, Object context) {
			return new MenuCanvas() {
				@Override
				public void fill(int left, int top, int right, int bottom, int color) { }

				@Override
				public void text(String text, int x, int y, int color) {
					drawn.add(text + "@" + x + "," + y);
				}

				@Override
				public int width(String text) {
					return text.codePointCount(0, text.length()) * 6;
				}
			};
		}

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
			return 640;
		}

		@Override
		public int height(Object screen) {
			return 360;
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
