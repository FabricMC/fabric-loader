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

package net.fabricmc.loader.api.config.util;

import java.util.Arrays;
import java.util.function.Supplier;

public abstract class StronglyTypedImmutableList<T, I> extends StronglyTypedImmutableCollection<Integer, T, I> {
	protected final T[] values;

	@SafeVarargs
	public StronglyTypedImmutableList(Class<T> valueClass, Supplier<T> defaultValue, T... values) {
		super(valueClass, defaultValue);
		this.values = values;
	}

	public StronglyTypedImmutableList(StronglyTypedImmutableList<T, I> other) {
		super(other.valueClass, other.defaultValue);
		this.values = Arrays.copyOf(other.values, other.values.length);
	}

	@Override
	public T get(Integer key) {
		return this.values[key];
	}

	@Override
	public int size() {
		return this.values.length;
	}
}
