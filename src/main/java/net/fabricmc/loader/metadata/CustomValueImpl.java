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

package net.fabricmc.loader.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.fabricmc.loader.api.metadata.CustomValue;

abstract class CustomValueImpl implements CustomValue {
	static final CustomValue BOOLEAN_TRUE = new BooleanImpl(true);
	static final CustomValue BOOLEAN_FALSE = new BooleanImpl(false);
	static final CustomValue NULL = new NullImpl();

	public static CustomValue fromJsonElement(JsonElement e) {
		if (e instanceof JsonObject) {
			JsonObject o = (JsonObject) e;
			Map<String, CustomValue> entries = new LinkedHashMap<>(o.size());

			for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
				entries.put(entry.getKey(), fromJsonElement(entry.getValue()));
			}

			return new ObjectImpl(entries);
		} else if (e instanceof JsonArray) {
			JsonArray o = (JsonArray) e;
			List<CustomValue> entries = new ArrayList<>(o.size());

			for (int i = 0, max = o.size(); i < max; i++) {
				entries.add(fromJsonElement(o.get(i)));
			}

			return new ArrayImpl(entries);
		} else if (e instanceof JsonPrimitive) {
			JsonPrimitive o = (JsonPrimitive) e;

			if (o.isString()) {
				return new StringImpl(o.getAsString());
			} else if (o.isNumber()) {
				return new NumberImpl(o.getAsNumber());
			} else if (o.isBoolean()) {
				return o.getAsBoolean() ? BOOLEAN_TRUE : BOOLEAN_FALSE;
			} else {
				throw new IllegalStateException();
			}
		} else if (e instanceof JsonNull) {
			return NULL;
		} else {
			throw new IllegalArgumentException(Objects.toString(e));
		}
	}

	@Override
	public final CvObject getAsObject() {
		if (this instanceof ObjectImpl) {
			return (ObjectImpl) this;
		} else {
			throw new ClassCastException("can't convert "+getType().name()+" to Object");
		}
	}

	@Override
	public final CvArray getAsArray() {
		if (this instanceof ArrayImpl) {
			return (ArrayImpl) this;
		} else {
			throw new ClassCastException("can't convert "+getType().name()+" to Array");
		}
	}

	@Override
	public final String getAsString() {
		if (this instanceof StringImpl) {
			return ((StringImpl) this).value;
		} else {
			throw new ClassCastException("can't convert "+getType().name()+" to String");
		}
	}

	@Override
	public final Number getAsNumber() {
		if (this instanceof NumberImpl) {
			return ((NumberImpl) this).value;
		} else {
			throw new ClassCastException("can't convert "+getType().name()+" to Number");
		}
	}

	@Override
	public final boolean getAsBoolean() {
		if (this instanceof BooleanImpl) {
			return ((BooleanImpl) this).value;
		} else {
			throw new ClassCastException("can't convert "+getType().name()+" to Boolean");
		}
	}

	private static final class ObjectImpl extends CustomValueImpl implements CvObject {
		private final Map<String, CustomValue> entries;

		public ObjectImpl(Map<String, CustomValue> entries) {
			this.entries = Collections.unmodifiableMap(entries);
		}

		@Override
		public CvType getType() {
			return CvType.OBJECT;
		}

		@Override
		public int size() {
			return entries.size();
		}

		@Override
		public boolean containsKey(String key) {
			return entries.containsKey(key);
		}

		@Override
		public CustomValue get(String key) {
			return entries.get(key);
		}

		@Override
		public Iterator<Entry<String, CustomValue>> iterator() {
			return entries.entrySet().iterator();
		}
	}

	private static final class ArrayImpl extends CustomValueImpl implements CvArray {
		private final List<CustomValue> entries;

		public ArrayImpl(List<CustomValue> entries) {
			this.entries = Collections.unmodifiableList(entries);
		}

		@Override
		public CvType getType() {
			return CvType.ARRAY;
		}

		@Override
		public int size() {
			return entries.size();
		}

		@Override
		public CustomValue get(int index) {
			return entries.get(index);
		}

		@Override
		public Iterator<CustomValue> iterator() {
			return entries.iterator();
		}
	}

	private static final class StringImpl extends CustomValueImpl {
		final String value;

		public StringImpl(String value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.STRING;
		}
	}

	private static final class NumberImpl extends CustomValueImpl {
		final Number value;

		public NumberImpl(Number value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.NUMBER;
		}
	}

	private static final class BooleanImpl extends CustomValueImpl {
		final boolean value;

		public BooleanImpl(boolean value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.BOOLEAN;
		}
	}

	private static final class NullImpl extends CustomValueImpl {
		@Override
		public CvType getType() {
			return CvType.NULL;
		}
	}
}
