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

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;

public class DefaultLanguageAdapter implements LanguageAdapter {
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
					//noinspection unchecked
					return (T) c.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					throw new LanguageAdapterException(e);
				}
			} else {
				throw new LanguageAdapterException("Class " + c.getName() + " cannot be cast to " + type.getName() + "!");
			}
		} else /* length == 2 */ {
			throw new LanguageAdapterException("Method handles not yet supported!");
		}
	}
}
