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

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

final class ModIcons {
	private static final int SIZE = 32;
	private final Map<String, List<int[]>> icons = new HashMap<>();

	void draw(MenuCanvas canvas, ModMetadata mod, int x, int y, int size) {
		List<int[]> pixels = icons.computeIfAbsent(mod.getId(), id -> read(mod));

		if (pixels.isEmpty()) {
			int color = 0xff365a70 | mod.getId().hashCode() & 0x00303030;
			canvas.fill(x, y, x + size, y + size, color);
			String initial = mod.getName().isEmpty() ? "?" : mod.getName().substring(0, mod.getName().offsetByCodePoints(0, 1));
			canvas.text(initial, x + (size - canvas.width(initial)) / 2, y + (size - 8) / 2, 0xffffffff);
			return;
		}

		for (int[] run : pixels) {
			canvas.fill(x + run[0] * size / SIZE, y + run[1] * size / SIZE,
					x + run[2] * size / SIZE, y + (run[1] + 1) * size / SIZE, run[3]);
		}
	}

	private List<int[]> read(ModMetadata mod) {
		List<int[]> result = new ArrayList<>();

		try {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(mod.getId());
			Optional<String> icon = mod.getIconPath(SIZE);
			if (!container.isPresent() || !icon.isPresent()) return result;
			Optional<Path> path = container.get().findPath(icon.get());
			if (!path.isPresent() || Files.size(path.get()) > 8 * 1024 * 1024) return result;
			Object image;

			try (InputStream input = Files.newInputStream(path.get())) {
				image = Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream.class).invoke(null, input);
			}

			if (image == null) return result;
			int width = (int) image.getClass().getMethod("getWidth").invoke(image);
			int height = (int) image.getClass().getMethod("getHeight").invoke(image);
			if (width == 0 || height == 0) return result;
			Method rgb = image.getClass().getMethod("getRGB", int.class, int.class);

			for (int y = 0; y < SIZE; y++) {
				int start = 0;
				int color = (int) rgb.invoke(image, 0, y * height / SIZE);

				for (int x = 1; x <= SIZE; x++) {
					int next = x == SIZE ? 0 : (int) rgb.invoke(image, x * width / SIZE, y * height / SIZE);

					if (x == SIZE || color != next) {
						if ((color >>> 24) != 0) result.add(new int[] { start, y, x, color });
						start = x;
						color = next;
					}
				}
			}
		} catch (ReflectiveOperationException | java.io.IOException | RuntimeException e) {
			result.clear();
		}

		return result;
	}
}
