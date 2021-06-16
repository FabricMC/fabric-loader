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

package net.fabricmc.loader.impl.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.lib.gson.JsonReader;

abstract class CustomValueImpl implements CustomValue {
	static final CustomValue BOOLEAN_TRUE = new BooleanImpl(true);
	static final CustomValue BOOLEAN_FALSE = new BooleanImpl(false);
	static final CustomValue NULL = new NullImpl();

	public static CustomValue readCustomValue(JsonReader reader) throws IOException, ParseMetadataException {
		switch (reader.peek()) {
		case BEGIN_OBJECT:
			reader.beginObject();

			// To preserve insertion order
			final Map<String, CustomValue> values = new LinkedHashMap<>();

			while (reader.hasNext()) {
				values.put(reader.nextName(), readCustomValue(reader));
			}

			reader.endObject();

			return new ObjectImpl(values);
		case BEGIN_ARRAY:
			reader.beginArray();

			final List<CustomValue> entries = new ArrayList<>();

			while (reader.hasNext()) {
				entries.add(readCustomValue(reader));
			}

			reader.endArray();

			return new ArrayImpl(entries);
		case STRING:
			return new StringImpl(reader.nextString());
		case NUMBER:
			// TODO: Parse this somewhat more smartly?
			return new NumberImpl(reader.nextDouble());
		case BOOLEAN:
			if (reader.nextBoolean()) {
				return BOOLEAN_TRUE;
			}

			return BOOLEAN_FALSE;
		case NULL:
			reader.nextNull();
			return NULL;
		default:
			throw new ParseMetadataException(Objects.toString(reader.nextName()), reader);
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

		ObjectImpl(Map<String, CustomValue> entries) {
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

		ArrayImpl(List<CustomValue> entries) {
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

		StringImpl(String value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.STRING;
		}
	}

	private static final class NumberImpl extends CustomValueImpl {
		final Number value;

		NumberImpl(Number value) {
			this.value = value;
		}

		@Override
		public CvType getType() {
			return CvType.NUMBER;
		}
	}

	private static final class BooleanImpl extends CustomValueImpl {
		final boolean value;

		BooleanImpl(boolean value) {
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
