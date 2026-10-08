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

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;

public final class ModsScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private final Screen parent;
	private final ModListModel model = new ModListModel(FabricLoader.getInstance().getAllMods());
	private String query = "";
	private int page;
	private List<ModMetadata> filtered;
	private ButtonWidget previous;
	private ButtonWidget next;
	private final List<ButtonWidget> rows = new ArrayList<>();

	public ModsScreen(Screen parent) {
		super(Text.translatable("fabricloader.mods.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		TextFieldWidget search = new TextFieldWidget(textRenderer, width / 2 - 100, 32, 200, 20,
				Text.translatable("fabricloader.mods.search"));
		search.setPlaceholder(Text.translatable("fabricloader.mods.search"));
		search.setText(query);
		search.setChangedListener(value -> {
			query = value;
			page = 0;
			refreshRows();
		});
		addDrawableChild(search);

		previous = addDrawableChild(ButtonWidget.builder(Text.translatable("fabricloader.mods.previous"), button -> {
			page--;
			clearAndInit();
		}).dimensions(width / 2 - 100, height - 52, 98, 20).build());
		next = addDrawableChild(ButtonWidget.builder(Text.translatable("fabricloader.mods.next"), button -> {
			page++;
			clearAndInit();
		}).dimensions(width / 2 + 2, height - 52, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
				.dimensions(width / 2 - 100, height - 28, 200, 20).build());
		refreshRows();
		setInitialFocus(search);
	}

	private int pageSize() {
		return Math.max(1, (height - 128) / ROW_HEIGHT);
	}

	private void refreshRows() {
		for (ButtonWidget row : rows) {
			remove(row);
		}

		rows.clear();
		filtered = model.filter(query);
		int pageSize = pageSize();
		int rowWidth = Math.min(300, width - 24);
		int lastPage = Math.max(0, (filtered.size() - 1) / pageSize);
		page = Math.max(0, Math.min(page, lastPage));
		previous.active = page > 0;
		next.active = page < lastPage;
		int end = Math.min(filtered.size(), (page + 1) * pageSize);

		for (int index = page * pageSize; index < end; index++) {
			ModMetadata mod = filtered.get(index);
			Text label = Text.literal(mod.getName() + "  " + mod.getVersion().getFriendlyString());
			ButtonWidget row = ButtonWidget.builder(label, button -> client.setScreen(new DetailsScreen(this, mod)))
					.dimensions((width - rowWidth) / 2, 74 + (index - page * pageSize) * ROW_HEIGHT, rowWidth, 20).build();
			row.setTooltip(Tooltip.of(label));
			rows.add(addDrawableChild(row));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("fabricloader.mods.count", filtered.size(), model.size()), width / 2, 58, 0xAAAAAA);

		if (filtered.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("fabricloader.mods.empty"), width / 2, 84, 0xAAAAAA);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	private static final class DetailsScreen extends Screen {
		private final Screen parent;
		private final ModMetadata mod;

		DetailsScreen(Screen parent, ModMetadata mod) {
			super(Text.literal(mod.getName()));
			this.parent = parent;
			this.mod = mod;
		}

		@Override
		protected void init() {
			int left = Math.max(12, width / 2 - 150);
			String authors = mod.getAuthors().stream().map(person -> person.getName()).collect(Collectors.joining(", "));
			Text details = Text.translatable("fabricloader.mods.details", mod.getId(),
					mod.getVersion().getFriendlyString(), authors, mod.getDescription());
			addDrawableChild(new ScrollableTextWidget(left, 38, width - left * 2, Math.max(20, height - 76), details, textRenderer));
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
					.dimensions(width / 2 - 100, height - 28, 200, 20).build());
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			renderBackground(context, mouseX, mouseY, delta);
			context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
			super.render(context, mouseX, mouseY, delta);
		}

		@Override
		public void close() {
			client.setScreen(parent);
		}
	}
}
