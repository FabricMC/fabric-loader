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

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.ModContainer;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.entrypoint.EntrypointContainerImpl;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.EntrypointMetadata;

import java.util.*;

class EntrypointStorage {
	interface Entry {
		<T> T getOrCreate(Class<T> type) throws Exception;

		ModContainer getModContainer();
	}

	private static class OldEntry implements Entry {
		private static final net.fabricmc.loader.language.LanguageAdapter.Options options = net.fabricmc.loader.language.LanguageAdapter.Options.Builder.create()
			.missingSuperclassBehaviour(net.fabricmc.loader.language.LanguageAdapter.MissingSuperclassBehavior.RETURN_NULL)
			.build();

		private final ModContainer mod;
		private final String languageAdapter;
		private final String value;
		private Object object;

		private OldEntry(ModContainer mod, String languageAdapter, String value) {
			this.mod = mod;
			this.languageAdapter = languageAdapter;
			this.value = value;
		}

		@Override
		public String toString() {
			return mod.getInfo().getId() + "->" + value;
		}

		@Override
		public <T> T getOrCreate(Class<T> type) throws Exception {
			if (object == null) {
				net.fabricmc.loader.language.LanguageAdapter adapter = (net.fabricmc.loader.language.LanguageAdapter) Class.forName(languageAdapter, true, FabricLauncherBase.getLauncher().getTargetClassLoader()).getConstructor().newInstance();
				object = adapter.createInstance(value, options);
			}

			if (object == null || !type.isAssignableFrom(object.getClass())) {
				return null;
			} else {
				//noinspection unchecked
				return (T) object;
			}
		}

		@Override
		public ModContainer getModContainer() {
			return mod;
		}
	}

	private static class NewEntry implements Entry {
		private final ModContainer mod;
		private final LanguageAdapter adapter;
		private final String value;
		private final Map<Class<?>, Object> instanceMap = new IdentityHashMap<>();

		private NewEntry(ModContainer mod, LanguageAdapter adapter, String value) {
			this.mod = mod;
			this.adapter = adapter;
			this.value = value;
		}

		@Override
		public String toString() {
			return mod.getInfo().getId() + "->(0.3.x)" + value;
		}

		@Override
		public <T> T getOrCreate(Class<T> type) throws Exception {
			Object o = instanceMap.get(type);
			if (o == null) {
				o = create(type);
				instanceMap.put(type, o);
			}
			//noinspection unchecked
			return (T) o;
		}

		@Override
		public ModContainer getModContainer() {
			return mod;
		}

		private <T> T create(Class<T> type) throws Exception {
			return adapter.create(mod, value, type);
		}
	}

	private final Map<String, List<Entry>> entryMap = new HashMap<>();

	private List<Entry> getOrCreateEntries(String key) {
		return entryMap.computeIfAbsent(key, (z) -> new ArrayList<>());
	}

	protected void addDeprecated(ModContainer modContainer, String adapter, String value) throws ClassNotFoundException, LanguageAdapterException {
		FabricLoader.INSTANCE.getLogger().debug("Registering 0.3.x old-style initializer " + value + " for mod " + modContainer.getInfo().getId());
		OldEntry oe = new OldEntry(modContainer, adapter, value);
		getOrCreateEntries("main").add(oe);
		getOrCreateEntries("client").add(oe);
		getOrCreateEntries("server").add(oe);
	}

	protected void add(ModContainer modContainer, String key, EntrypointMetadata metadata, Map<String, LanguageAdapter> adapterMap) throws Exception {
		if (!adapterMap.containsKey(metadata.getAdapter())) {
			throw new Exception("Could not find adapter '" + metadata.getAdapter() + "' (mod " + modContainer.getInfo().getId() + "!)");
		}

		FabricLoader.INSTANCE.getLogger().debug("Registering new-style initializer " + metadata.getValue() + " for mod " + modContainer.getInfo().getId() + " (key " + key + ")");
		getOrCreateEntries(key).add(new NewEntry(
			modContainer, adapterMap.get(metadata.getAdapter()), metadata.getValue()
		));
	}

	boolean hasEntrypoints(String key) {
		return entryMap.containsKey(key);
	}

	protected <T> List<T> getEntrypoints(String key, Class<T> type) {
		List<Entry> entries = entryMap.get(key);
		if (entries == null) return Collections.emptyList();

		EntrypointException exception = null;
		List<T> results = new ArrayList<>(entries.size());

		for (Entry entry : entries) {
			try {
				T result = entry.getOrCreate(type);

				if (result != null) {
					results.add(result);
				}
			} catch (Throwable t) {
				if (exception == null) {
					exception = new EntrypointException(key, entry.getModContainer().getMetadata().getId(), t);
				} else {
					exception.addSuppressed(t);
				}
			}
		}

		if (exception != null) {
			throw exception;
		}

		return results;
	}

	protected <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		List<Entry> entries = entryMap.get(key);
		if (entries == null) return Collections.emptyList();

		EntrypointException exception = null;
		List<EntrypointContainer<T>> results = new ArrayList<>(entries.size());

		for (Entry entry : entries) {
			try {
				T result = entry.getOrCreate(type);

				if (result != null) {
					results.add(new EntrypointContainerImpl<>(entry.getModContainer(), result));
				}
			} catch (Throwable t) {
				if (exception == null) {
					exception = new EntrypointException(key, entry.getModContainer().getMetadata().getId(), t);
				} else {
					exception.addSuppressed(t);
				}
			}
		}

		if (exception != null) {
			throw exception;
		}

		return results;
	}
}
