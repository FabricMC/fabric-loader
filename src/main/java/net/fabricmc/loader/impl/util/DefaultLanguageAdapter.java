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

package net.fabricmc.loader.impl.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	private DefaultLanguageAdapter() { }

	@SuppressWarnings("unchecked")
	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		String[] methodSplit = value.split("::");

		if (methodSplit.length >= 3) {
			throw new LanguageAdapterException("Invalid handle format: " + value);
		}

		Class<?> c;

		try {
			c = Class.forName(methodSplit[0], true, FabricLauncherBase.getLauncher().getTargetClassLoader());
		} catch (ClassNotFoundException e) {
			throw new LanguageAdapterException(e);
		}

		if (methodSplit.length == 1) {
			if (type.isAssignableFrom(c)) {
				try {
					return (T) c.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					throw new LanguageAdapterException(e);
				}
			} else {
				throw new LanguageAdapterException("Class " + c.getName() + " cannot be cast to " + type.getName() + "!");
			}
		} else /* length == 2 */ {
			List<Method> methodList = new ArrayList<>();

			for (Method m : c.getDeclaredMethods()) {
				if (!(m.getName().equals(methodSplit[1]))) {
					continue;
				}

				methodList.add(m);
			}

			try {
				Field field = c.getDeclaredField(methodSplit[1]);
				Class<?> fType = field.getType();

				if ((field.getModifiers() & Modifier.STATIC) == 0) {
					throw new LanguageAdapterException("Field " + value + " must be static!");
				}

				if (!methodList.isEmpty()) {
					throw new LanguageAdapterException("Ambiguous " + value + " - refers to both field and method!");
				}

				if (!type.isAssignableFrom(fType)) {
					throw new LanguageAdapterException("Field " + value + " cannot be cast to " + type.getName() + "!");
				}

				return (T) field.get(null);
			} catch (NoSuchFieldException e) {
				// ignore
			} catch (IllegalAccessException e) {
				throw new LanguageAdapterException("Field " + value + " cannot be accessed!", e);
			}

			if (!type.isInterface()) {
				throw new LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.getName() + "!");
			}

			if (methodList.isEmpty()) {
				throw new LanguageAdapterException("Could not find " + value + "!");
			} else if (methodList.size() >= 2) {
				throw new LanguageAdapterException("Found multiple method entries of name " + value + "!");
			}

			final Method targetMethod = methodList.get(0);
			Object object = null;

			if ((targetMethod.getModifiers() & Modifier.STATIC) == 0) {
				try {
					object = c.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					throw new LanguageAdapterException(e);
				}
			}

			MethodHandle handle;

			try {
				handle = MethodHandles.lookup()
						.unreflect(targetMethod);
			} catch (Exception ex) {
				throw new LanguageAdapterException(ex);
			}

			if (object != null) {
				handle = handle.bindTo(object);
			}

			// uses proxy as well, but this handles default and object methods
			try {
				return MethodHandleProxies.asInterfaceInstance(type, handle);
			} catch (Exception ex) {
				throw new LanguageAdapterException(ex);
			}
		}
	}
}
