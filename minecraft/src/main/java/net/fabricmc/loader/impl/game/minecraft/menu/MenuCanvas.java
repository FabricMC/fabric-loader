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

interface MenuCanvas {
	MenuCanvas EMPTY = new MenuCanvas() {
		@Override
		public void fill(int left, int top, int right, int bottom, int color) { }

		@Override
		public void text(String text, int x, int y, int color) { }

		@Override
		public int width(String text) {
			return text.length() * 6;
		}
	};

	void fill(int left, int top, int right, int bottom, int color);

	void text(String text, int x, int y, int color);

	int width(String text);

	default String shorten(String value, int width) {
		if (width(value) <= width) return value;
		int end = value.length();

		while (end > 0 && width(value.substring(0, end) + "...") > width) {
			end = value.offsetByCodePoints(end, -1);
		}

		return value.substring(0, end) + "...";
	}

	default void border(int left, int top, int right, int bottom, int color) {
		fill(left, top, right, top + 1, color);
		fill(left, bottom - 1, right, bottom, color);
		fill(left, top, left + 1, bottom, color);
		fill(right - 1, top, right, bottom, color);
	}
}
