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

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Supplier;

public class Array<T> extends StronglyTypedImmutableList<T, T> {
    @SafeVarargs
    public Array(Class<T> valueClass, Supplier<T> defaultValue, T... values) {
        super(valueClass, defaultValue, values);
    }

    public Array(Array<T> other) {
        super(other);
    }

	@Override
	public Array<T> addEntry() {
		T[] values = Arrays.copyOf(this.values, this.values.length + 1);
		values[this.values.length] = this.defaultValue.get();

		return new Array<>(this.valueClass, this.defaultValue, values);
	}

	@Override
	public Array<T> set(Integer key, T value) {
		T[] values = Arrays.copyOf(this.values, this.values.length);
		values[key] = value;

		return new Array<>(this.valueClass, this.defaultValue, values);
	}

	@Override
	public Array<T> remove(int index) {
		//noinspection unchecked
		T[] values = (T[]) java.lang.reflect.Array.newInstance(this.valueClass, this.values.length - 1);

		int i = 0;
		for (int j = 0; j < this.values.length; ++i, ++j) {
			if (j == index) ++j;

			values[i] = this.values[j];
		}

		return new Array<>(this.valueClass, this.defaultValue, values);
	}

	@NotNull
    @Override
    public Iterator<T> iterator() {
        return Arrays.asList(this.values).iterator();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Array[");

        for (int i = 0; i < this.values.length; ++i) {
            builder.append(this.values[i]);

            if (i < this.values.length - 1) {
                builder.append(", ");
            }
        }

        builder.append(']');

        return builder.toString();
    }

	@Override
	public Iterable<T> getValues() {
		return this;
	}
}
