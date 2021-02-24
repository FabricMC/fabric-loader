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

import java.util.function.Supplier;

/**
 * This class represents a data structure similar to a collection, with two caveats. Firstly, it is strongly typed,
 * meaning we can always get the type of the values in this collection, even when it's empty. Secondly, it is immutable,
 * and any operations that would normally add to a collection returns a new instance instead.
 *
 * @param <K> Key type
 * @param <V> Value type
 * @param <I> Iterator type
 */
public abstract class StronglyTypedImmutableCollection<K, V, I> implements Iterable<I>, ValueCollection<V> {
    protected final Class<V> valueClass;
    protected final Supplier<V> defaultValue;

    public StronglyTypedImmutableCollection(Class<V> valueClass, Supplier<V> defaultValue) {
        this.valueClass = valueClass;
        this.defaultValue = defaultValue;
    }

    public final Class<V> getValueClass() {
        return this.valueClass;
    }

	/**
	 * @return a new instance of this class, with space for an additional entry appended to it
	 */
    public abstract StronglyTypedImmutableCollection<K, V, I> addEntry();
    public abstract V get(K key);

    public Supplier<V> getDefaultValue() {
        return this.defaultValue;
    }

	/**
	 * Creates a new instance of this class, with the value at key set to v.
	 * @param key the key to change
	 * @param v the new value
	 * @return a new instance of this class
	 */
    public abstract StronglyTypedImmutableCollection<K, V, I> set(K key, V v);

    public abstract StronglyTypedImmutableCollection<K, V, I> remove(int index);

    public abstract int size();
}
