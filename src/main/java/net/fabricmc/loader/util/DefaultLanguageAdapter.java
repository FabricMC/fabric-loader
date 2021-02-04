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

package net.fabricmc.loader.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;

public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	private DefaultLanguageAdapter() {

	}

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
					// can't call exact for return type isn't specified
					//noinspection unchecked
					return (T) MethodHandles.lookup().findConstructor(c, MethodType.methodType(Void.TYPE)).invoke();
				} catch (Throwable e) {
					throw new LanguageAdapterException(e);
				}
			}
			throw new LanguageAdapterException("Class " + c.getName() + " cannot be cast to " + type.getName() + "!");
		}

		/* length == 2 */
		Method foundMethod = null;

		for (Method m : c.getDeclaredMethods()) {
			if (!(m.getName().equals(methodSplit[1]))) {
				continue;
			}

			if (foundMethod != null) {
				throw new LanguageAdapterException("Found multiple method entries of name " + value + "!");
			}

			foundMethod = m;
		}

		try {
			Field field = c.getDeclaredField(methodSplit[1]);
			Class<?> fType = field.getType();
			if ((field.getModifiers() & Modifier.STATIC) == 0) {
				throw new LanguageAdapterException("Field " + value + " must be static!");
			}

			if (foundMethod != null) {
				throw new LanguageAdapterException("Ambiguous " + value + " - refers to both field and method!");
			}

			if (!type.isAssignableFrom(fType)) {
				throw new LanguageAdapterException("Field " + value + " cannot be cast to " + type.getName() + "!");
			}

			//noinspection unchecked
			return (T) field.get(null);
		} catch (NoSuchFieldException e) {
			// ignore
		} catch (IllegalAccessException e) {
			throw new LanguageAdapterException("Field " + value + " cannot be accessed!", e);
		}

		if (foundMethod == null) {
			throw new LanguageAdapterException("Could not find " + value + "!");
		}

		if (!type.isInterface()) {
			throw new LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.getName() + "!");
		}

		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle called;
		try {
			called = lookup.unreflect(foundMethod);
		} catch (IllegalAccessException ex) {
			throw new LanguageAdapterException("Method " + value + " is not accessible to the language adapter", ex);
		}

		if (!Modifier.isStatic(foundMethod.getModifiers())) {
			try {
				called = called.bindTo(lookup.findConstructor(c, MethodType.methodType(Void.TYPE)).invoke());
			} catch (Throwable e) {
				throw new LanguageAdapterException("Cannot create a class instance to invoke instance method for " + value, e);
			}
		}

		final MethodHandle target = called;
		// cannot use lambda meta factory because hidden classes will not work
		//noinspection unchecked
		return (T) Proxy.newProxyInstance(FabricLauncherBase.getLauncher().getTargetClassLoader(), new Class[] {type}, (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				switch (method.getName()) {
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return (proxy == args[0] ? Boolean.TRUE : Boolean.FALSE);
				case "toString":
					return proxy.getClass().getName() + '@' + Integer.toHexString(proxy.hashCode());
				}
			}

			try {
				return target.invokeWithArguments(args);
			} catch (WrongMethodTypeException | ClassCastException ex) {
				throw new UnsupportedOperationException("Cannot delegate call to backing method " + value, ex);
			}
		});
	}
}
