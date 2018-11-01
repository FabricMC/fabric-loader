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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JavaLanguageAdapter implements ILanguageAdapter {
	@Override
	public Object createInstance(Class<?> modClass) throws LanguageAdapterException {
		try {
			Constructor<?> constructor = modClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new LanguageAdapterException("Could not find constructor for class " + modClass.getName() + "!", e);
		} catch (IllegalAccessException e) {
			throw new LanguageAdapterException("!?", e);
		} catch (InvocationTargetException | IllegalArgumentException | InstantiationException e) {
			throw new LanguageAdapterException("Could not instantiate class " + modClass.getName() + "!", e);
		}
	}
}
