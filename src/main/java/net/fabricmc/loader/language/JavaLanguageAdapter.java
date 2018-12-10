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

package net.fabricmc.loader.language;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class JavaLanguageAdapter implements LanguageAdapter {
	private static boolean canApplyInterface(String itfString) throws IOException {
		String className = itfString.replace('/', '.');
		byte[] data = ((LaunchClassLoader) JavaLanguageAdapter.class.getClassLoader()).getClassBytes(className);
		if (data == null) {
			return false;
		}
		ClassReader reader = new ClassReader(data);

		// TODO: Be a bit more involved
		if (className.equals("net/fabricmc/api/ClientModInitializer.class")) {
			if (FabricLoader.INSTANCE.getEnvironmentHandler().getEnvironmentType() == EnvType.SERVER) {
				return false;
			}
		} else if (className.equals("net/fabricmc/api/DedicatedServerModInitializer.class")) {
			if (FabricLoader.INSTANCE.getEnvironmentHandler().getEnvironmentType() == EnvType.CLIENT) {
				return false;
			}
		}

		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				return false;
			}
		}

		return true;
	}

	public static Class<?> getClass(String classString, Options options) throws ClassNotFoundException, IOException {
		byte[] data = ((LaunchClassLoader) JavaLanguageAdapter.class.getClassLoader()).getClassBytes(classString);
		if (data == null) {
			throw new ClassNotFoundException("Could not find file " + classString);
		}

		ClassReader reader = new ClassReader(data);
		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				switch (options.getMissingSuperclassBehavior()) {
					case RETURN_NULL:
						return null;
					case CRASH:
					default:
						throw new ClassNotFoundException("Could not find or load class " + s);

				}
			}
		}

		return Class.forName(classString);
	}

	@Override
	public Object createInstance(Class<?> modClass, Options options) throws LanguageAdapterException {
		try {
			Constructor<?> constructor = modClass.getDeclaredConstructor();
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new LanguageAdapterException("Could not find constructor for class " + modClass.getName() + "!", e);
		} catch (IllegalAccessException e) {
			throw new LanguageAdapterException("Could not access constructor of class " + modClass.getName() + "!", e);
		} catch (InvocationTargetException | IllegalArgumentException | InstantiationException e) {
			throw new LanguageAdapterException("Could not instantiate class " + modClass.getName() + "!", e);
		}
	}
}
