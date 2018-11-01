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

package net.fabricmc.loader;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.language.ILanguageAdapter;
import net.fabricmc.loader.language.LanguageAdapterException;

import java.util.Collection;

class InstanceStorage {
	private final Multimap<Class, Object> instances = HashMultimap.create();

	@SuppressWarnings("unchecked")
	public <T> Collection<T> getInitializers(Class<T> type) {
		return (Collection<T>) instances.get(type);
	}

	protected void instantiate(String name, ILanguageAdapter adapter, boolean force) {
		try {
			Object o = adapter.createInstance(name);
			add(o);
		} catch (ClassNotFoundException | LanguageAdapterException e) {
			if (force) {
				throw new RuntimeException(e);
			} else {
				return;
			}
		}
	}

	protected final void add(Object o) {
		add(o, o.getClass());
	}

	protected void add(Object o, Class c) {
		if (c == null) {
			return;
		}

		instances.put(c, o);
		add(o, c.getSuperclass());
		for (Class ci : c.getInterfaces()) {
			add(o, ci);
		}
	}
}
