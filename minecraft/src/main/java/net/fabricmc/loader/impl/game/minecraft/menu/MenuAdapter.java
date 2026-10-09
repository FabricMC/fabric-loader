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

public interface MenuAdapter {
	default String translate(String key, String fallback) {
		return fallback;
	}

	default int[] titleButtonBounds(Object screen) {
		return new int[] { width(screen) / 2 + 2, height(screen) / 4 + 96, 98, 20 };
	}

	default Object search(Object screen, int x, int y, int width, int height, String value) {
		return null;
	}

	default String searchValue(Object search) {
		return "";
	}

	default MenuCanvas canvas(Object screen, Object context) {
		return MenuCanvas.EMPTY;
	}

	default void renderSearch(Object search, Object context, int mouseX, int mouseY, float delta) {
	}

	default void openLink(String address) {
	}

	default void openModsFolder() {
	}

	Object text(String text);

	Object screen(Object state);

	Object button(int x, int y, int width, int height, String label, Runnable action);

	void add(Object screen, Object button);

	int width(Object screen);

	int height(Object screen);

	void open(Object screen);

	void active(Object button, boolean active);
}
