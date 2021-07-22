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

package net.fabricmc.loader.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import net.fabricmc.loader.api.ObjectShare;

final class ObjectShareImpl implements ObjectShare {
	private final Map<String, Object> values = new HashMap<>();
	private final Map<String, List<BiConsumer<String, Object>>> pending = new HashMap<>();

	@Override
	public synchronized Object get(String key) {
		validateKey(key);

		return values.get(key);
	}

	@Override
	public synchronized Object put(String key, Object value) {
		validateKey(key);
		Objects.requireNonNull(value, "null value");

		Object prev = values.put(key, value);

		if (prev == null) {
			invokePending(key, value);
		}

		return prev;
	}

	@Override
	public synchronized Object putIfAbsent(String key, Object value) {
		validateKey(key);
		Objects.requireNonNull(value, "null value");

		Object prev = values.putIfAbsent(key, value);

		if (prev == null) {
			invokePending(key, value);
		}

		return prev;
	}

	@Override
	public synchronized Object remove(String key) {
		validateKey(key);

		return values.remove(key);
	}

	@Override
	public synchronized void whenAvailable(String key, BiConsumer<String, Object> consumer) {
		validateKey(key);

		Object value = get(key);

		if (value != null) {
			consumer.accept(key, value);
		} else {
			pending.computeIfAbsent(key, ignore -> new ArrayList<>()).add(consumer);
		}
	}

	private static void validateKey(String key) {
		Objects.requireNonNull(key, "null key");

		int pos = key.indexOf(':');
		if (pos <= 0 || pos >= key.length() - 1) throw new IllegalArgumentException("invalid key, must be modid:subkey");
	}

	private void invokePending(String key, Object value) {
		List<BiConsumer<String, Object>> pendingEntries = pending.remove(key);

		if (pendingEntries != null) {
			for (BiConsumer<String, Object> consumer : pendingEntries) {
				consumer.accept(key, value);
			}
		}
	}
}
