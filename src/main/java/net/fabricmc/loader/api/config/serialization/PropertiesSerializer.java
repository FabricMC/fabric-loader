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

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.api.config.value.ValueContainer;
import org.jetbrains.annotations.Nullable;

/**
 * Default {@link ConfigSerializer} implementation.
 *
 * Supports serialization of comments, constraints, and data.
 */
public class PropertiesSerializer implements ConfigSerializer<Map<String, String>> {
	public static final PropertiesSerializer INSTANCE = new PropertiesSerializer();
	private final HashMap<Class<?>, ValueSerializer<?>> serializableTypes = new HashMap<>();

	protected PropertiesSerializer() {
		this.addSerializer(Boolean.class, BooleanSerializer.INSTANCE);
		this.addSerializer(Integer.class, IntSerializer.INSTANCE);
		this.addSerializer(Long.class, LongSerializer.INSTANCE);
		this.addSerializer(String.class, StringSerializer.INSTANCE);
		this.addSerializer(Float.class, FloatSerializer.INSTANCE);
		this.addSerializer(Double.class, DoubleSerializer.INSTANCE);
	}

	protected final <T> void addSerializer(Class<T> valueClass, ValueSerializer<T> valueSerializer) {
		this.serializableTypes.putIfAbsent(valueClass, valueSerializer);

		//noinspection unchecked
		valueClass = (Class<T>) ReflectionUtil.getClass(valueClass);

		for (Class<?> clazz : ReflectionUtil.getClasses(valueClass)) {
			this.serializableTypes.putIfAbsent(clazz, valueSerializer);
		}
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(ValueKey<V> valueKey) {
		return (ValueSerializer<V>) this.getSerializer(valueKey.getDefaultValue().getClass());
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(Class<V> valueClass) {
		return (ValueSerializer<V>) serializableTypes.get(valueClass);
	}

	@Override
	public void serialize(ConfigDefinition<Map<String, String>> configDefinition, OutputStream outputStream, ValueContainer valueContainer, Predicate<ValueKey<?>> valuePredicate, boolean minimal) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

		boolean header = false;

		if (!minimal) {
			for (String comment : configDefinition.getData(DataType.COMMENT)) {
				for (String s : comment.split("\\r?\\n")) {
					writer.write("# " + s + '\n');
				}

				header = true;
			}
		}

		writer.write("version=" + configDefinition.getVersion().toString() + '\n');

		Iterator<ValueKey<?>> iterator = configDefinition.iterator();

		while (iterator.hasNext()) {
			ValueKey<?> value = iterator.next();

			if (valuePredicate.test(value)) {
				if (!minimal) {
					for (String comment : value.getData(DataType.COMMENT)) {
						if (header) {
							writer.write('\n');
							header = false;
						}

						for (String s : comment.split("\\r?\\n")) {
							writer.write("# " + s + '\n');
						}
					}

					for (Flag flag : value.getFlags()) {
						if (header) {
							writer.write('\n');
							header = false;
						}

						writer.write("# " + flag.toString() + '\n');
					}

					for (Constraint<?> constraint : value.getConstraints()) {
						if (header) {
							writer.write('\n');
							header = false;
						}

						writer.write("# " + constraint.toString() + '\n');
					}
				}

				//noinspection rawtypes
				ValueSerializer serializer = this.getSerializer(value);
				writer.write(value.getPathString());
				writer.write('=');

				//noinspection unchecked
				writer.write(serializer.serialize(valueContainer.get(value)));

				if (iterator.hasNext()) writer.write("\n\n");
				header = false;
			}
		}

		writer.flush();
		writer.close();
	}

	@Override
	public void deserialize(ConfigDefinition<Map<String, String>> configDefinition, InputStream inputStream, ValueContainer valueContainer) throws IOException {
		Map<String, String> values = this.getRepresentation(inputStream);

		//noinspection rawtypes
		for (ValueKey value : configDefinition) {
			String valueString = values.get(value.getPathString());

			if (valueString == null) {
				continue;
			}

			//noinspection unchecked
			value.setValue(this.getSerializer(value).deserialize(valueString), valueContainer);
		}

    }

	@Override
	public @NotNull String getExtension() {
		return "properties";
	}

	@Override
	public @Nullable SemanticVersion getVersion(InputStream inputStream) throws IOException, VersionParsingException {
		String s = this.getRepresentation(inputStream).get("version");
		return s == null ? null : SemanticVersion.parse(s);
	}

	@Override
	public @NotNull Map<String, String> getRepresentation(InputStream inputStream) throws IOException {
		Map<String, String> values = new HashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (!line.startsWith("!") && !line.startsWith("#") && !line.trim().isEmpty()) {
				String[] split = line.split("=", 2);

				values.put(split[0], split[1]);
			}
		}

		return values;
	}

	public interface ValueSerializer<V> {
		String serialize(V value);
		V deserialize(String representation);
	}

	private static class IntSerializer implements ValueSerializer<Integer> {
		public static final ValueSerializer<Integer> INSTANCE = new IntSerializer();

		@Override
		public String serialize(Integer value) {
			return value.toString();
		}

		@Override
		public Integer deserialize(String representation) {
			return Integer.valueOf(representation);
		}
	}

	private static class LongSerializer implements ValueSerializer<Long> {
		public static final ValueSerializer<Long> INSTANCE = new LongSerializer();

		@Override
		public String serialize(Long value) {
			return value.toString();
		}

		@Override
		public Long deserialize(String representation) {
			return Long.valueOf(representation);
		}
	}

	private static class FloatSerializer implements ValueSerializer<Float> {
		public static final ValueSerializer<Float> INSTANCE = new FloatSerializer();

		@Override
		public String serialize(Float value) {
			return value.toString();
		}

		@Override
		public Float deserialize(String representation) {
			return Float.valueOf(representation);
		}
	}

	private static class DoubleSerializer implements ValueSerializer<Double> {
		public static final ValueSerializer<Double> INSTANCE = new DoubleSerializer();

		@Override
		public String serialize(Double value) {
			return value.toString();
		}

		@Override
		public Double deserialize(String representation) {
			return Double.valueOf(representation);
		}
	}

	private static class StringSerializer implements ValueSerializer<String> {
		public static final ValueSerializer<String> INSTANCE = new StringSerializer();

		@Override
		public String serialize(String value) {
			return value;
		}

		@Override
		public String deserialize(String representation) {
			return representation;
		}
	}

	private static class BooleanSerializer implements ValueSerializer<Boolean> {
		public static final ValueSerializer<Boolean> INSTANCE = new BooleanSerializer();

		@Override
		public String serialize(Boolean value) {
			return value.toString();
		}

		@Override
		public Boolean deserialize(String representation) {
			return Boolean.valueOf(representation);
		}
	}
}
