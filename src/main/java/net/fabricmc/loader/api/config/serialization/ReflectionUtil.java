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

package net.fabricmc.loader.api.config.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

/**
 * Handles primitive to wrapper class conversion.
 */
public class ReflectionUtil {
	private static final BiMap<Class<?>, Class<?>> PRIMITIVE_TO_OBJECT_CLASS_MAP;

	static {
		ImmutableBiMap.Builder<Class<?>, Class<?>> builder = ImmutableBiMap.builder();

		builder.put(Boolean.TYPE, Boolean.class);
		builder.put(Character.TYPE, Character.class);
		builder.put(Byte.TYPE, Byte.class).put(Short.TYPE, Short.class);
		builder.put(Integer.TYPE, Integer.class);
		builder.put(Long.TYPE, Long.class);
		builder.put(Float.TYPE, Float.class);
		builder.put(Double.TYPE, Double.class);

		PRIMITIVE_TO_OBJECT_CLASS_MAP = builder.build();
	}

	public static Class<?> getClass(Class<?> potentialPrimitive) {
		return PRIMITIVE_TO_OBJECT_CLASS_MAP.getOrDefault(potentialPrimitive, potentialPrimitive);
	}

	/**
	 * Gets all compatible classes for a given class.
	 *
	 * @param clazz the class to check
	 * @return an array of equivalent classes
	 */
	public static Class<?>[] getClasses(Class<?> clazz) {
		return PRIMITIVE_TO_OBJECT_CLASS_MAP.containsKey(clazz)
				? new Class<?>[] {clazz, PRIMITIVE_TO_OBJECT_CLASS_MAP.get(clazz)}
				: PRIMITIVE_TO_OBJECT_CLASS_MAP.inverse().containsKey(clazz)
				? new Class<?>[] {clazz, PRIMITIVE_TO_OBJECT_CLASS_MAP.inverse().get(clazz)}
				: new Class<?>[] {clazz};
	}
}
