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

package net.fabricmc.base;

import com.google.gson.*;

import java.lang.reflect.Type;

public enum Side {
	CLIENT,
	SERVER,
	UNIVERSAL;

	public static class Serializer implements JsonSerializer<Side>, JsonDeserializer<Side> {

		@Override
		public JsonElement serialize(Side side, Type type, JsonSerializationContext jsonSerializationContext) {
			return new JsonPrimitive(side.name().toLowerCase());
		}

		@Override
		public Side deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			return Side.valueOf(element.getAsString().toUpperCase());
		}
	}

}
