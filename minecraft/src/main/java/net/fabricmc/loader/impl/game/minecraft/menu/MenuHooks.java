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
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class MenuHooks {
	public static final String INTERNAL_NAME = "net/fabricmc/loader/impl/game/minecraft/menu/MenuHooks";
	private static final int ROW = 38;
	private static MenuAdapter adapter;
	private static boolean unavailable;

	private MenuHooks() { }

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
		return "Mods";
	}

	public static void init(Object screen, Object state, MenuAdapter api) {
		State menu = (State) state;
		menu.width = api.width(screen);
		menu.height = api.height(screen);
		menu.left = menu.width / 2 - 6;
		menu.right = menu.left + 16;
		menu.bottom = menu.height - 36;
		menu.search = api.search(screen, 8, 24, Math.max(20, menu.left - 34), 20, menu.query);
		api.add(screen, api.button(menu.left - 20, 24, 20, 20, "+", () -> {
			menu.libraries = !menu.libraries;
			menu.refresh(menu.query);
		}));
		int half = (menu.width - menu.right - 12) / 2;
		menu.website = api.button(menu.right, 90, half, 20, api.translate("fabric.mods.website", "Website"), () -> link(menu, api, "homepage"));
		menu.issues = api.button(menu.right + half + 4, 90, half, 20, api.translate("fabric.mods.issues", "Issues"), () -> link(menu, api, "issues"));
		api.add(screen, menu.website);
		api.add(screen, menu.issues);
		int buttonWidth = Math.min(150, (menu.width - 24) / 2);
		api.add(screen, api.button(menu.width / 2 - buttonWidth - 4, menu.height - 26, buttonWidth, 20,
				api.translate("fabric.mods.open_folder", "Open Mods Folder"), api::openModsFolder));
		api.add(screen, api.button(menu.width / 2 + 4, menu.height - 26, buttonWidth, 20, api.translate("gui.done", "Done"), () -> close(state, api)));
		menu.refresh(menu.query);
	}

	public static void render(Object screen, Object state, MenuAdapter api, Object context, int mouseX, int mouseY, float delta) {
		State menu = (State) state;
		menu.mouseX = mouseX;
		menu.mouseY = mouseY;

		if (menu.search != null) {
			String query = api.searchValue(menu.search);
			if (!menu.query.equals(query)) menu.refresh(query);
		}

		MenuCanvas canvas = api.canvas(screen, context);
		canvas.text("Mods", (menu.left + 8 - canvas.width("Mods")) / 2, 8, 0xffffffff);
		canvas.text(menu.mods.size() + (menu.mods.size() == 1 ? " mod" : " mods") + (menu.libraries ? " (including libraries)" : ""), 8, 54, 0xffeeeeee);
		canvas.fill(0, 68, menu.left + 6, menu.bottom, 0x99000000);
		canvas.fill(menu.right, 116, menu.width - 8, menu.bottom, 0x99000000);
		int max = Math.max(0, menu.mods.size() * ROW - (menu.bottom - 70));
		menu.listScroll = Math.max(0, Math.min(menu.listScroll, max));

		for (int index = menu.listScroll / ROW; index < menu.mods.size(); index++) {
			int y = 70 + index * ROW - menu.listScroll;
			if (y + ROW > menu.bottom) break;
			if (y < 70) continue;
			ModMetadata mod = menu.mods.get(index);
			boolean selected = menu.mod != null && menu.mod.getId().equals(mod.getId());

			if (selected || mouseX >= 4 && mouseX < menu.left && mouseY >= y && mouseY < y + ROW) {
				canvas.fill(4, y, menu.left, y + ROW, selected ? 0xbb000000 : 0x55555555);
				if (selected) canvas.border(4, y, menu.left, y + ROW, 0xffaaaaaa);
			}

			menu.icons.draw(canvas, mod, 8, y + 3, 32);
			canvas.text(canvas.shorten(mod.getName(), menu.left - 50), 44, y + 3, 0xffffffff);
			List<String> description = wrap(canvas, safe(mod.getDescription()).isEmpty() ? mod.getId() : mod.getDescription(), menu.left - 50);
			for (int line = 0; line < Math.min(2, description.size()); line++) canvas.text(description.get(line), 44, y + 15 + line * 10, 0xff999999);
		}

		scrollbar(canvas, menu.left, 70, menu.bottom, menu.listScroll, max);
		api.active(menu.website, contact(menu.mod, "homepage") != null);
		api.active(menu.issues, contact(menu.mod, "issues") != null);
		menu.links.clear();

		if (menu.mod != null) {
			ModMetadata mod = menu.mod;
			menu.icons.draw(canvas, mod, menu.right, 50, 32);
			canvas.text(canvas.shorten(mod.getName(), menu.width - menu.right - 48), menu.right + 36, 50, 0xffffffff);
			canvas.text(canvas.shorten(mod.getVersion().getFriendlyString(), menu.width - menu.right - 48), menu.right + 36, 62, 0xffaaaaaa);
			String authors = mod.getAuthors().stream().map(person -> person.getName()).collect(Collectors.joining(", "));
			canvas.text(canvas.shorten("By " + authors, menu.width - menu.right - 48), menu.right + 36, 74, 0xffaaaaaa);
			List<Line> lines = new ArrayList<>();
			add(lines, canvas, safe(mod.getDescription()), menu.width - menu.right - 20, 0xffdddddd, null);
			lines.add(new Line("", 0, null));
			add(lines, canvas, "ID  " + mod.getId(), menu.width - menu.right - 20, 0xffaaaaaa, null);
			if (mod.getEnvironment() == ModEnvironment.CLIENT) lines.add(new Line("Client mod", 0xff8ebeff, null));
			if (mod.getContact() != null && !mod.getContact().asMap().isEmpty()) lines.add(new Line("", 0, null));

			if (mod.getContact() != null) {
				for (Map.Entry<String, String> link : mod.getContact().asMap().entrySet()) {
					if (!link.getValue().startsWith("https://") && !link.getValue().startsWith("http://")) continue;
					String name = link.getKey();
					if (name.isEmpty()) continue;
					add(lines, canvas, Character.toUpperCase(name.charAt(0)) + name.substring(1), menu.width - menu.right - 20, 0xff7799ff, link.getValue());
				}
			}

			lines.add(new Line("", 0, null));
			add(lines, canvas, "License", menu.width - menu.right - 20, 0xffaaaaaa, null);
			add(lines, canvas, String.join(", ", mod.getLicense()), menu.width - menu.right - 20, 0xffdddddd, null);
			lines.add(new Line("", 0, null));
			add(lines, canvas, "Authors", menu.width - menu.right - 20, 0xffaaaaaa, null);
			add(lines, canvas, authors, menu.width - menu.right - 20, 0xffdddddd, null);
			menu.detailsMax = Math.max(0, lines.size() * 11 - (menu.bottom - 120));
			menu.detailsScroll = Math.max(0, Math.min(menu.detailsScroll, menu.detailsMax));

			for (int index = menu.detailsScroll / 11; index < lines.size(); index++) {
				int y = 120 + index * 11 - menu.detailsScroll;
				if (y + 10 > menu.bottom) break;
				if (y < 120) continue;
				Line line = lines.get(index);
				canvas.text(line.text, menu.right + 4, y, line.color);

				if (line.url != null) {
					menu.links.add(new Link(y, canvas.width(line.text), line.url));

					if (mouseX >= menu.right + 4 && mouseX < menu.right + 4 + canvas.width(line.text) && mouseY >= y && mouseY < y + 10) {
						canvas.fill(menu.right + 4, y + 9, menu.right + 4 + canvas.width(line.text), y + 10, line.color);
					}
				}
			}

			scrollbar(canvas, menu.width - 10, 120, menu.bottom, menu.detailsScroll, menu.detailsMax);
		} else {
			canvas.text("No matching mods", menu.right + 8, 124, 0xffaaaaaa);
		}

		api.renderSearch(menu.search, context, mouseX, mouseY, delta);
	}

	private static void scrollbar(MenuCanvas canvas, int x, int top, int bottom, int scroll, int max) {
		if (max == 0 || bottom <= top) return;
		int height = bottom - top;
		int handle = Math.max(16, height * height / (height + max));
		int y = top + scroll * (height - handle) / max;
		canvas.fill(x - 4, top, x, bottom, 0xff111111);
		canvas.fill(x - 4, y, x, y + handle, 0xffaaaaaa);
		canvas.fill(x - 4, y, x - 1, y + handle - 1, 0xffcccccc);
	}

	public static boolean input(Object state, MenuAdapter api, String kind, Object[] args) {
		State menu = (State) state;
		double[] point = "mouseScrolled".equals(kind) && args.length == 1 && args[0] instanceof Number
				? new double[] { menu.mouseX, menu.mouseY, 0 } : MenuInput.pointer(args);
		if (point == null) return false;
		double x = point[0];
		double y = point[1];

		if ("mouseReleased".equals(kind) && menu.drag != 0) {
			menu.drag = 0;
			return true;
		}

		if ("mouseDragged".equals(kind) && menu.drag != 0) {
			drag(menu, y);
			return true;
		}

		if ("mouseScrolled".equals(kind)) {
			int amount = (int) Math.round(-((Number) args[args.length - 1]).doubleValue() * 18);

			if (x < menu.left + 6 && y >= 70 && y < menu.bottom) {
				menu.listScroll = Math.max(0, Math.min(menu.listScroll + amount, Math.max(0, menu.mods.size() * ROW - (menu.bottom - 70))));
				return true;
			}

			if (x >= menu.right && y >= 116 && y < menu.bottom) {
				menu.detailsScroll = Math.max(0, Math.min(menu.detailsScroll + amount, menu.detailsMax));
				return true;
			}
		}

		if (!"mouseClicked".equals(kind) || point[2] != 0) return false;

		if (x >= menu.left - 4 && x <= menu.left && y >= 70 && y < menu.bottom) menu.drag = 1;
		if (x >= menu.width - 14 && x <= menu.width - 10 && y >= 120 && y < menu.bottom) menu.drag = 2;

		if (menu.drag != 0) {
			drag(menu, y);
			return true;
		}

		if (x >= 4 && x < menu.left && y >= 70 && y < menu.bottom) {
			int index = (int) (y - 70 + menu.listScroll) / ROW;

			if (index >= 0 && index < menu.mods.size()) {
				menu.mod = menu.mods.get(index);
				menu.detailsScroll = 0;
			}

			return true;
		}

		for (Link link : menu.links) {
			if (x >= menu.right + 4 && x < menu.right + 4 + link.width && y >= link.y && y < link.y + 10) {
				api.openLink(link.url);
				return true;
			}
		}

		return false;
	}

	private static void drag(State menu, double y) {
		int top = menu.drag == 1 ? 70 : 120;
		int max = menu.drag == 1 ? Math.max(0, menu.mods.size() * ROW - (menu.bottom - top)) : menu.detailsMax;
		int value = (int) Math.round((y - top) * max / Math.max(1, menu.bottom - top));
		value = Math.max(0, Math.min(max, value));

		if (menu.drag == 1) {
			menu.listScroll = value;
		} else {
			menu.detailsScroll = value;
		}
	}

	private static void link(State menu, MenuAdapter api, String key) {
		String link = contact(menu.mod, key);
		if (link != null) api.openLink(link);
	}

	private static String contact(ModMetadata mod, String key) {
		if (mod == null || mod.getContact() == null) return null;
		return mod.getContact().get(key).filter(value -> value.startsWith("https://") || value.startsWith("http://")).orElse(null);
	}

	public static void close(Object state, MenuAdapter api) {
		api.open(((State) state).parent);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static void add(List<Line> lines, MenuCanvas canvas, String value, int width, int color, String url) {
		for (String text : wrap(canvas, value, width)) lines.add(new Line(text, color, url));
	}

	static List<String> wrap(MenuCanvas canvas, String value, int width) {
		List<String> lines = new ArrayList<>();

		for (String paragraph : value.split("\\R", -1)) {
			String remaining = paragraph;

			while (!remaining.isEmpty() && canvas.width(remaining) > width) {
				int end = 0;

				while (end < remaining.length()) {
					int next = remaining.offsetByCodePoints(end, 1);
					if (end > 0 && canvas.width(remaining.substring(0, next)) > width) break;
					end = next;
				}

				int space = remaining.lastIndexOf(' ', end);
				if (space > 0) end = space;
				lines.add(remaining.substring(0, end));
				remaining = remaining.substring(end);
				if (remaining.startsWith(" ")) remaining = remaining.substring(1);
			}

			lines.add(remaining);
		}

		return lines;
	}

	private static final class Line {
		final String text;
		final int color;
		final String url;

		Line(String text, int color, String url) {
			this.text = text;
			this.color = color;
			this.url = url;
		}
	}

	private static final class Link {
		final int y;
		final int width;
		final String url;

		Link(int y, int width, String url) {
			this.y = y;
			this.width = width;
			this.url = url;
		}
	}

	static final class State {
		final Object parent;
		final ModListModel model;
		final ModIcons icons = new ModIcons();
		final List<Link> links = new ArrayList<>();
		List<ModMetadata> mods;
		ModMetadata mod;
		String query = "";
		boolean libraries;
		int width;
		int height;
		int left;
		int right;
		int bottom;
		int listScroll;
		int detailsScroll;
		int detailsMax;
		int drag;
		int mouseX;
		int mouseY;
		Object search;
		Object website;
		Object issues;

		State(Object parent, ModListModel model, ModMetadata mod) {
			this.parent = parent;
			this.model = model;
			this.mod = mod;
			refresh("");
		}

		void refresh(String query) {
			this.query = query;
			mods = model.filter(query, libraries);
			if (mod == null || mods.stream().noneMatch(entry -> entry.getId().equals(mod.getId()))) mod = mods.isEmpty() ? null : mods.get(0);
			listScroll = 0;
			detailsScroll = 0;
		}
	}
}
