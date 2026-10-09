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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class MinecraftMenuCanvas implements MenuCanvas {
	private final Object screen;
	private final Object font;
	private final Method fill;
	private final Method text;
	private final Method measure;
	private final boolean graphics;
	private final boolean fontText;
	private Object context;

	MinecraftMenuCanvas(MinecraftMenuAdapter api, Object screen, Object context) throws ReflectiveOperationException {
		this.screen = screen;
		this.context = context;
		font = api.font();
		measure = api.method(font.getClass(), m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class && m.getReturnType() == int.class,
				"method_1727", "width", "getWidth", "getStringWidth");
		Method rectangle = null;
		Method lettering = null;

		if (context != null) {
			try {
				rectangle = api.method(context.getClass(), m -> ints(m.getParameterTypes(), 0, 5), "method_25294", "fill");
				lettering = api.method(context.getClass(), m -> graphicsText(m, font, 6), "method_51433", "drawText", "drawString", "text");
			} catch (NoSuchMethodException e) {
				if (rectangle != null) {
					lettering = api.method(context.getClass(), m -> graphicsText(m, font, 5), "method_51439", "method_25303", "drawTextWithShadow", "drawString", "text");
				}
			}
		}

		graphics = rectangle != null && lettering != null;
		boolean onFont = false;

		if (!graphics) {
			rectangle = api.method(screen.getClass(), m -> {
				Class<?>[] p = m.getParameterTypes();
				return ints(p, 0, 5) || p.length == 6 && context != null && p[0].isInstance(context) && ints(p, 1, 5);
			}, "method_1785", "method_25294", "fill");

			try {
				lettering = api.method(screen.getClass(), m -> graphicsText(m, font, 5), "method_1789", "drawString", "drawTextWithShadow");
			} catch (NoSuchMethodException e) {
				onFont = true;
				lettering = api.method(font.getClass(), m -> fontText(m, context), "method_1720", "method_27517", "method_27521", "drawWithShadow", "drawShadow", "draw");
			}
		}

		fill = rectangle;
		text = lettering;
		fontText = onFont;
	}

	void context(Object context) {
		this.context = context;
	}

	private static boolean ints(Class<?>[] types, int start, int count) {
		if (types.length != start + count) return false;

		for (int i = start; i < types.length; i++) {
			if (types[i] != int.class) return false;
		}

		return true;
	}

	private static boolean graphicsText(Method method, Object font, int count) {
		Class<?>[] p = method.getParameterTypes();
		return p.length == count && p[0].isInstance(font) && p[1] == String.class
				&& p[2] == int.class && p[3] == int.class && p[4] == int.class && (count == 5 || p[5] == boolean.class);
	}

	private static boolean fontText(Method method, Object context) {
		Class<?>[] p = method.getParameterTypes();
		int start = p.length > 0 && p[0] == String.class ? 0 : 1;
		return (p.length == start + 4 || p.length == start + 5) && (start == 0 || context != null && p[0].isInstance(context))
				&& p[start] == String.class && p[start + 1] == float.class && p[start + 2] == float.class && p[start + 3] == int.class
				&& (p.length == start + 4 || p[start + 4] == boolean.class);
	}

	@Override
	public void fill(int left, int top, int right, int bottom, int color) {
		if (right <= left || bottom <= top) return;
		call(fill, graphics ? context : screen, fill.getParameterCount() == 5
				? new Object[] { left, top, right, bottom, color } : new Object[] { context, left, top, right, bottom, color });
	}

	@Override
	public void text(String value, int x, int y, int color) {
		Object[] args;

		if (fontText) {
			Class<?>[] p = text.getParameterTypes();
			int offset = p[0] == String.class ? 0 : 1;
			args = new Object[p.length];
			if (offset == 1) args[0] = context;
			args[offset] = value;
			args[offset + 1] = (float) x;
			args[offset + 2] = (float) y;
			args[offset + 3] = color;
			if (args.length == offset + 5) args[offset + 4] = true;
		} else {
			args = text.getParameterCount() == 6 ? new Object[] { font, value, x, y, color, true } : new Object[] { font, value, x, y, color };
		}

		call(text, fontText ? font : graphics ? context : screen, args);
	}

	@Override
	public int width(String value) {
		return ((Number) call(measure, font, new Object[] { value })).intValue();
	}

	private static Object call(Method method, Object target, Object[] args) {
		try {
			return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not draw the Fabric mods menu", e);
		}
	}
}
