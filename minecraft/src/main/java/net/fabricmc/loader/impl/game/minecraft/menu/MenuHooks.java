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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class MenuHooks {
	public static final String INTERNAL_NAME = "net/fabricmc/loader/impl/game/minecraft/menu/MenuHooks";
	private static MenuAdapter adapter;
	private static boolean unavailable;

	private MenuHooks() {
	}

	public static void addTitleButton(Object title) {
		FabricLoader loader = FabricLoader.getInstance();

		if (!isEnabled(loader.getEnvironmentType(), loader.isModLoaded("modmenu"), System.getProperty("fabric.modsMenu")) || unavailable) return;

		try {
			if (adapter == null) {
				adapter = new MinecraftMenuAdapter(title.getClass().getClassLoader());
			}

			MenuAdapter api = adapter;
			int[] bounds = api.titleButtonBounds(title);
			api.add(title, api.button(bounds[0], bounds[1], bounds[2], bounds[3], "Mods", () -> {
				State state = new State(title, new ModListModel(loader.getAllMods()), null);
				api.open(api.screen(state));
			}));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			unavailable = true;
			Log.error(LogCategory.GAME_PATCH, "Could not create the built-in mods menu", e);
		}
	}

	static boolean isEnabled(EnvType environment, boolean modMenuLoaded, String setting) {
		return environment == EnvType.CLIENT && !modMenuLoaded && !"false".equalsIgnoreCase(setting);
	}

	public static String title(Object state) {
		State menu = (State) state;
		return menu.mod == null ? "Mods" : menu.mod.getName();
	}

	public static void init(Object screen, Object state, MenuAdapter api) {
		State menu = (State) state;
		int width = api.width(screen);
		int height = api.height(screen);
		int rowWidth = Math.max(20, Math.min(360, width - 24));
		int left = (width - rowWidth) / 2;
		int pageSize = Math.max(1, (height - 110) / 22);
		List<ModMetadata> mods = menu.model.filter("");
		List<String> details = menu.mod == null ? null : details(menu.mod, Math.max(8, rowWidth / 7));
		int count = details == null ? mods.size() : details.size();
		int lastPage = Math.max(0, (count - 1) / pageSize);
		menu.page = Math.max(0, Math.min(menu.page, lastPage));
		label(api, screen, left, 8, rowWidth, title(state));
		label(api, screen, left, 30, rowWidth, (menu.page + 1) + " / " + (lastPage + 1) + (details == null ? "  (" + count + ")" : ""));
		int end = Math.min(count, (menu.page + 1) * pageSize);

		for (int index = menu.page * pageSize; index < end; index++) {
			int y = 54 + (index - menu.page * pageSize) * 22;

			if (details == null) {
				ModMetadata mod = mods.get(index);
				api.add(screen, api.button(left, y, rowWidth, 20, mod.getName() + "  " + mod.getVersion().getFriendlyString(),
						() -> api.open(api.screen(new State(screen, menu.model, mod)))));
			} else {
				label(api, screen, left, y, rowWidth, details.get(index));
			}
		}

		Object previous = api.button(width / 2 - 100, height - 52, 98, 20, "<", () -> changePage(menu, api, -1));
		Object next = api.button(width / 2 + 2, height - 52, 98, 20, ">", () -> changePage(menu, api, 1));
		api.active(previous, menu.page > 0);
		api.active(next, menu.page < lastPage);
		api.add(screen, previous);
		api.add(screen, next);
		api.add(screen, api.button(width / 2 - 100, height - 28, 200, 20, api.translate("gui.back", "Back"), () -> close(state, api)));
	}

	private static void label(MenuAdapter api, Object screen, int x, int y, int width, String text) {
		Object label = api.button(x, y, width, 20, text, () -> { });
		api.active(label, false);
		api.add(screen, label);
	}

	private static void changePage(State state, MenuAdapter api, int delta) {
		state.page += delta;
		api.open(api.screen(state));
	}

	public static void close(Object state, MenuAdapter api) {
		api.open(((State) state).parent);
	}

	static List<String> details(ModMetadata mod, int columns) {
		List<String> lines = new ArrayList<>();
		wrap(lines, mod.getId(), columns);
		wrap(lines, mod.getVersion().getFriendlyString(), columns);
		wrap(lines, mod.getAuthors().stream().map(person -> person.getName()).collect(Collectors.joining(", ")), columns);
		wrap(lines, mod.getDescription(), columns);
		return lines;
	}

	private static void wrap(List<String> lines, String value, int columns) {
		for (String paragraph : value.split("\\R", -1)) {
			int offset = 0;
			int remaining = paragraph.codePointCount(0, paragraph.length());

			while (remaining > columns) {
				int end = paragraph.offsetByCodePoints(offset, columns);
				int space = paragraph.lastIndexOf(' ', end);
				if (space > offset) end = space;
				lines.add(paragraph.substring(offset, end));
				remaining -= paragraph.codePointCount(offset, end);
				offset = end;

				if (offset < paragraph.length() && paragraph.charAt(offset) == ' ') {
					offset++;
					remaining--;
				}
			}

			lines.add(paragraph.substring(offset));
		}
	}

	static final class State {
		final Object parent;
		final ModListModel model;
		final ModMetadata mod;
		int page;

		State(Object parent, ModListModel model, ModMetadata mod) {
			this.parent = parent;
			this.model = model;
			this.mod = mod;
		}
	}
}
