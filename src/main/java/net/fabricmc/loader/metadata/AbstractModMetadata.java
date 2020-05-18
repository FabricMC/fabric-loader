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

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

public abstract class AbstractModMetadata implements ModMetadata {
	@Override
	public boolean containsCustomElement(String key) {
		return containsCustomValue(key);
	}

	@Override
	public JsonElement getCustomElement(String key) {
		CustomValue value = getCustomValue(key);

		return value != null ? convert(value) : null;
	}

	@Override
	public boolean containsCustomValue(String key) {
		return getCustomValues().containsKey(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return getCustomValues().get(key);
	}

	private static JsonElement convert(CustomValue value) {
		switch (value.getType()) {
		case ARRAY: {
			JsonArray ret = new JsonArray();

			for (CustomValue v : value.getAsArray()) {
				ret.add(convert(v));
			}

			return ret;
		}
		case BOOLEAN:
			return new JsonPrimitive(value.getAsBoolean());
		case NULL:
			return JsonNull.INSTANCE;
		case NUMBER:
			return new JsonPrimitive(value.getAsNumber());
		case OBJECT: {
			JsonObject ret = new JsonObject();

			for (Map.Entry<String, CustomValue> entry : value.getAsObject()) {
				ret.add(entry.getKey(), convert(entry.getValue()));
			}

			return ret;
		}
		case STRING:
			return new JsonPrimitive(value.getAsString());
		}

		throw new IllegalStateException();
	}
}
