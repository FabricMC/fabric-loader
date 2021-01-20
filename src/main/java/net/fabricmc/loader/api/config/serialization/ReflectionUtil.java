package net.fabricmc.loader.api.config.serialization;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

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

	public static Class<?>[] getClasses(Class<?> clazz) {
		return PRIMITIVE_TO_OBJECT_CLASS_MAP.containsKey(clazz)
				? new Class<?>[] {clazz, PRIMITIVE_TO_OBJECT_CLASS_MAP.get(clazz)}
				: PRIMITIVE_TO_OBJECT_CLASS_MAP.inverse().containsKey(clazz)
				? new Class<?>[] {clazz, PRIMITIVE_TO_OBJECT_CLASS_MAP.inverse().get(clazz)}
				: new Class<?>[] {clazz};
	}
}
