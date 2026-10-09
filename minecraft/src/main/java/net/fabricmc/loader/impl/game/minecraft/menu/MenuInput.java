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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

final class MenuInput {
	private MenuInput() { }

	static double[] pointer(Object[] args) {
		if (args.length >= 2 && args[0] instanceof Number && args[1] instanceof Number) {
			return new double[] { ((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue(), args.length > 2 && args[2] instanceof Integer ? ((Number) args[2]).doubleValue() : 0 };
		}

		if (args.length == 0 || args[0] == null) return null;

		try {
			Object event = args[0];
			double x = ((Number) event.getClass().getMethod("x").invoke(event)).doubleValue();
			double y = ((Number) event.getClass().getMethod("y").invoke(event)).doubleValue();
			Object button = event.getClass().getMethod("button").invoke(event);
			if (!(button instanceof Number)) button = button.getClass().getMethod("button").invoke(button);
			return new double[] { x, y, ((Number) button).doubleValue() };
		} catch (ReflectiveOperationException e) {
			return fields(args[0]);
		}
	}

	private static double[] fields(Object event) {
		double[] result = new double[3];
		int coordinate = 0;
		Object info = null;

		try {
			for (Field field : event.getClass().getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true);

				if (field.getType() == double.class && coordinate < 2) {
					result[coordinate++] = field.getDouble(event);
				} else if (!field.getType().isPrimitive()) {
					info = field.get(event);
				}
			}

			if (coordinate != 2 || info == null) return null;

			for (Field field : info.getClass().getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
				field.setAccessible(true);
				result[2] = field.getInt(info);
				return result;
			}
		} catch (ReflectiveOperationException e) {
			return null;
		}

		return null;
	}
}
