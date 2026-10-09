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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.objectweb.asm.Type;

import net.fabricmc.loader.api.FabricLoader;

final class MinecraftMenuAdapter implements MenuAdapter {
	private final MenuMappings mappings;
	private final Class<?> screenClass;
	private final Class<?> buttonClass;
	private final Constructor<?> screenConstructor;
	private final Field width;
	private final Field height;
	private final Field active;
	private final Method add;
	private final Object client;
	private final Method setScreen;
	private Constructor<?> literal;
	private Method textFactory;
	private Constructor<?> buttonConstructor;
	private Method builderFactory;
	private Method bounds;
	private Method build;
	private Class<?> actionType;
	private Method actionMethod;
	private Method translate;
	private boolean legacy;

	MinecraftMenuAdapter(ClassLoader loader) throws ReflectiveOperationException {
		this(loader, new MenuMappings(FabricLoader.getInstance().getMappingResolver()));
	}

	MinecraftMenuAdapter(ClassLoader loader, MenuMappings mappings) throws ReflectiveOperationException {
		this.mappings = mappings;
		screenClass = load(loader, "net/minecraft/class_437", "net/minecraft/client/gui/screens/Screen", "net/minecraft/client/gui/screen/Screen");
		Class<?> button;

		try {
			button = load(loader, "net/minecraft/class_4185", "net/minecraft/client/gui/components/Button", "net/minecraft/client/gui/widget/ButtonWidget");
		} catch (ClassNotFoundException e) {
			button = load(loader, "net/minecraft/class_339", "net/minecraft/client/gui/widget/ClickableWidget");
		}

		buttonClass = button;
		screenConstructor = loader.loadClass("net.fabricmc.loader.impl.game.minecraft.menu.GeneratedModsScreen").getConstructor(Object.class, MenuAdapter.class);
		width = field(screenClass, "field_22789", "field_2561", "width");
		height = field(screenClass, "field_22790", "field_2559", "height");
		active = field(buttonClass, "field_22763", "field_2078", "active", "enabled");
		add = method(screenClass, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(buttonClass),
				"method_37063", "method_25411", "method_2219", "addDrawableChild", "addRenderableWidget", "addButton");
		Class<?> minecraft = load(loader, "net/minecraft/class_310", "net/minecraft/client/Minecraft", "net/minecraft/client/MinecraftClient");
		Method getInstance = method(minecraft, m -> Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0,
				"method_1551", "getInstance");
		client = getInstance.invoke(null);
		setScreen = method(minecraft, m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == screenClass,
				"method_1507", "setScreen", "setScreenAndShow", "openScreen");
		bindText(loader);
		bindButton(loader);

		try {
			Class<?> i18n = load(loader, "net/minecraft/class_1074", "net/minecraft/client/resources/language/I18n", "net/minecraft/client/resource/language/I18n");
			translate = method(i18n, m -> Modifier.isStatic(m.getModifiers()) && m.getReturnType() == String.class && m.getParameterCount() == 2,
					"method_4662", "get", "translate");
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			translate = null;
		}
	}

	@Override
	public int[] titleButtonBounds(Object screen) {
		try {
			List<Object> widgets = new ArrayList<>();

			for (Class<?> type = screenClass; type != null; type = type.getSuperclass()) {
				for (Field candidate : type.getDeclaredFields()) {
					if (Modifier.isStatic(candidate.getModifiers()) || !Iterable.class.isAssignableFrom(candidate.getType())) continue;
					candidate.setAccessible(true);
					Object value = candidate.get(screen);
					if (value != null) ((Iterable<?>) value).forEach(widgets::add);
				}
			}

			String realms = translate("menu.online", "Minecraft Realms");

			for (Object widget : widgets) {
				if (!buttonClass.isInstance(widget)) continue;
				Object text = field(widget.getClass(), "field_22754", "field_2074", "message").get(widget);
				if (text == null) continue;
				String label = text instanceof String ? (String) text : (String) method(text.getClass(),
						m -> m.getParameterCount() == 0 && m.getReturnType() == String.class, "method_10851", "getString", "asString").invoke(text);
				if (!realms.equals(label) && !"Minecraft Realms".equals(label)) continue;
				Field x = field(widget.getClass(), "field_22760", "field_2069", "x");
				Field y = field(widget.getClass(), "field_22761", "field_2068", "y");
				Field widgetWidth = field(widget.getClass(), "field_22758", "field_2071", "width");
				Field widgetHeight = field(widget.getClass(), "field_22759", "field_2070", "height");
				int originalWidth = widgetWidth.getInt(widget);
				if (originalWidth < 44) continue;
				int leftWidth = Math.max(20, (originalWidth - 4) / 2);
				int[] result = { x.getInt(widget) + leftWidth + 4, y.getInt(widget), originalWidth - leftWidth - 4, widgetHeight.getInt(widget) };
				widgetWidth.setInt(widget, leftWidth);
				return result;
			}
		} catch (ReflectiveOperationException e) {
			// Keep a usable position if another mod replaces the title widgets.
		}

		return MenuAdapter.super.titleButtonBounds(screen);
	}

	@Override
	public String translate(String key, String fallback) {
		if (translate == null) return fallback;

		try {
			String value = (String) translate.invoke(null, key, new Object[0]);
			return key.equals(value) ? fallback : value;
		} catch (ReflectiveOperationException e) {
			throw failure(e);
		}
	}

	private void bindText(ClassLoader loader) throws ReflectiveOperationException {
		try {
			Class<?> text = load(loader, "net/minecraft/class_2561", "net/minecraft/network/chat/Component", "net/minecraft/text/Text");
			textFactory = method(text, m -> Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class,
					"method_43470", "method_30163", "literal", "of");
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			try {
				literal = load(loader, "net/minecraft/class_2585", "net/minecraft/text/LiteralText", "net/minecraft/network/chat/TextComponent").getConstructor(String.class);
			} catch (ClassNotFoundException e2) {
				literal = null;
			}
		}
	}

	private void bindButton(ClassLoader loader) throws ReflectiveOperationException {
		for (Method candidate : buttonClass.getDeclaredMethods()) {
			Class<?>[] parameters = candidate.getParameterTypes();

			if (Modifier.isStatic(candidate.getModifiers()) && parameters.length == 2 && parameters[1].isInterface()
					&& mappings.method(Type.getInternalName(buttonClass), Type.getMethodDescriptor(candidate), candidate.getName(), "method_46430", "builder")
					&& candidate.getReturnType() != buttonClass && callback(parameters[1]) != null) {
				builderFactory = candidate;
				actionType = parameters[1];
				actionMethod = callback(actionType);
				Class<?> builder = candidate.getReturnType();
				bounds = method(builder, m -> allInts(m.getParameterTypes(), 4), "method_46434", "dimensions", "bounds");
				build = method(builder, m -> m.getParameterCount() == 0 && m.getReturnType() == buttonClass, "method_46431", "build");
				return;
			}
		}

		for (Constructor<?> candidate : buttonClass.getDeclaredConstructors()) {
			Class<?>[] parameters = candidate.getParameterTypes();

			if (parameters.length == 6 && firstInts(parameters, 4) && parameters[5].isInterface() && callback(parameters[5]) != null) {
				buttonConstructor = candidate;
				buttonConstructor.setAccessible(true);
				actionType = parameters[5];
				actionMethod = callback(actionType);
				return;
			}
		}

		buttonConstructor = loader.loadClass("net.fabricmc.loader.impl.game.minecraft.menu.GeneratedMenuButton")
				.getConstructor(int.class, int.class, int.class, int.class, String.class, Runnable.class, MenuAdapter.class);
		legacy = true;
	}

	private static boolean allInts(Class<?>[] types, int count) {
		return types.length == count && firstInts(types, count);
	}

	private static boolean firstInts(Class<?>[] types, int count) {
		for (int i = 0; i < count; i++) {
			if (types[i] != int.class) return false;
		}

		return true;
	}

	private Method callback(Class<?> type) {
		Method result = null;

		for (Method method : type.getMethods()) {
			if (Modifier.isAbstract(method.getModifiers())) {
				if (result != null || method.getReturnType() != void.class) return null;
				result = method;
			}
		}

		return result;
	}

	@Override
	public Object text(String text) {
		try {
			return textFactory != null ? textFactory.invoke(null, text) : literal != null ? literal.newInstance(text) : text;
		} catch (ReflectiveOperationException e) {
			throw failure(e);
		}
	}

	@Override
	public Object screen(Object state) {
		try {
			return screenConstructor.newInstance(state, this);
		} catch (ReflectiveOperationException e) {
			throw failure(e);
		}
	}

	@Override
	public Object button(int x, int y, int width, int height, String label, Runnable action) {
		try {
			if (legacy) return buttonConstructor.newInstance(x, y, width, height, label, action, this);
			Object callback = Proxy.newProxyInstance(actionType.getClassLoader(), new Class<?>[] { actionType }, (proxy, method, arguments) -> {
				if (method.equals(actionMethod)) {
					action.run();
					return null;
				}

				if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
				if ("equals".equals(method.getName())) return proxy == arguments[0];
				if ("toString".equals(method.getName())) return "Fabric mods menu action";
				throw new UnsupportedOperationException(method.toString());
			});
			Class<?> labelType = builderFactory != null ? builderFactory.getParameterTypes()[0] : buttonConstructor.getParameterTypes()[4];
			Object message = labelType == String.class ? label : text(label);

			if (builderFactory != null) {
				Object builder = builderFactory.invoke(null, message, callback);
				bounds.invoke(builder, x, y, width, height);
				return build.invoke(builder);
			}

			return buttonConstructor.newInstance(x, y, width, height, message, callback);
		} catch (ReflectiveOperationException e) {
			throw failure(e);
		}
	}

	@Override
	public void add(Object screen, Object button) {
		invoke(add, screen, button);
	}

	@Override
	public int width(Object screen) {
		return integer(width, screen);
	}

	@Override
	public int height(Object screen) {
		return integer(height, screen);
	}

	@Override
	public void open(Object screen) {
		invoke(setScreen, client, screen);
	}

	@Override
	public void active(Object button, boolean value) {
		try {
			active.setBoolean(button, value);
		} catch (IllegalAccessException e) {
			throw failure(e);
		}
	}

	private int integer(Field field, Object owner) {
		try {
			return field.getInt(owner);
		} catch (IllegalAccessException e) {
			throw failure(e);
		}
	}

	private void invoke(Method method, Object owner, Object... arguments) {
		try {
			method.invoke(owner, arguments);
		} catch (ReflectiveOperationException e) {
			throw failure(e);
		}
	}

	private Field field(Class<?> owner, String... names) throws NoSuchFieldException {
		for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (mappings.field(Type.getInternalName(type), Type.getDescriptor(field.getType()), field.getName(), names)) {
					field.setAccessible(true);
					return field;
				}
			}
		}

		throw new NoSuchFieldException(owner.getName() + ": " + String.join(", ", names));
	}

	private Method method(Class<?> owner, Predicate<Method> shape, String... names) throws NoSuchMethodException {
		for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
			for (Method method : type.getDeclaredMethods()) {
				if (shape.test(method) && mappings.method(Type.getInternalName(type), Type.getMethodDescriptor(method), method.getName(), names)) {
					method.setAccessible(true);
					return method;
				}
			}

			for (Class<?> contract : type.getInterfaces()) {
				try {
					return method(contract, shape, names);
				} catch (NoSuchMethodException e) {
					// Continue with the remaining interfaces and superclass.
				}
			}
		}

		throw new NoSuchMethodException(owner.getName() + ": " + String.join(", ", names));
	}

	private Class<?> load(ClassLoader loader, String... names) throws ClassNotFoundException {
		for (String name : names) {
			try {
				return loader.loadClass(mappings.className(name).replace('/', '.'));
			} catch (ClassNotFoundException e) {
				// Try the next namespace.
			}
		}

		throw new ClassNotFoundException(String.join(", ", names));
	}

	private IllegalStateException failure(ReflectiveOperationException error) {
		return new IllegalStateException("Could not use the Minecraft mods menu", error);
	}
}
