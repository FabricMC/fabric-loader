package net.fabricmc.loader.api.config.serialization;

import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.data.Constraint;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.data.Flag;
import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.SemanticVersion;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default {@link ConfigSerializer} implementation.
 *
 * Supports serialization of comments, constraints, and data.
 */
public class PropertiesSerializer implements ConfigSerializer {
	public static final ConfigSerializer INSTANCE = new PropertiesSerializer();
	private final HashMap<Class<?>, ValueSerializer> serializableTypes = new HashMap<>();
	private final HashMap<Class<?>, Function<ConfigValue<?>, ValueSerializer<?>>> typeDependentSerializers = new HashMap<>();

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

	@SuppressWarnings({"unchecked", "rawtypes"})
	protected final <T> void addSerializer(Class<T> valueClass, Function<ConfigValue<T>, ValueSerializer<T>> serializerBuilder) {
		this.typeDependentSerializers.putIfAbsent(valueClass, (Function) serializerBuilder);
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(ConfigValue<V> configValue) {
		V defaultValue = configValue.getDefaultValue();

		if (typeDependentSerializers.containsKey(defaultValue.getClass())) {
			return (ValueSerializer<V>) typeDependentSerializers.get(defaultValue.getClass()).apply(configValue);
		}

		return (ValueSerializer<V>) this.getSerializer(defaultValue.getClass());
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(Supplier<V> defaultValue) {
		return this.getSerializer((Class<V>) defaultValue.get().getClass());
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(Class<V> valueClass) {
		return (ValueSerializer<V>) serializableTypes.get(valueClass);
	}

	@Override
	public void serialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(this.getPath(configDefinition, valueContainer));

		boolean header = false;

		for (String comment : configDefinition.getData(DataType.COMMENT)) {
			for (String s : comment.split("\\r?\\n")) {
				writer.write("# " + s + '\n');
			}

			header = true;
		}

		Iterator<ConfigValue<?>> iterator = configDefinition.iterator();

		while (iterator.hasNext()) {
			ConfigValue<?> value = iterator.next();

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

			ValueSerializer serializer = this.getSerializer(value);
			writer.write(value.getKey().getPathString());
			writer.write('=');
			writer.write(serializer.serialize(value.get()));

			if (iterator.hasNext()) writer.write("\n");
			header = false;
		}

		writer.flush();
		writer.close();
	}

	@Override
	public void deserialize(ConfigDefinition configDefinition, ValueContainer valueContainer) throws IOException {
		this.deserialize(configDefinition, Files.newInputStream(this.getPath(configDefinition, valueContainer)), valueContainer);
	}

	@Override
	public void deserialize(ConfigDefinition configDefinition, InputStream inputStream, ValueContainer valueContainer) throws IOException {
		Map<String, String> values = new HashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (!line.startsWith("!") && !line.startsWith("#") && !line.trim().isEmpty()) {
				String[] split = line.split("=", 2);

				values.put(split[0], split[1]);
			}
		}

		for (ConfigValue value : configDefinition) {
			String valueString = values.get(value.getKey().getPathString());

			if (valueString == null) {
				ConfigManager.LOGGER.warn("Failed to load config value '{}'", value.getKey());
				continue;
			}

			value.set(this.getSerializer(value).deserialize(valueString), valueContainer);
		}
	}

	@Override
	public @Nullable SemanticVersion getVersion(ConfigDefinition configDefinition, ValueContainer valueContainer) throws Exception {
		Map<String, String> values = new HashMap<>();

		Files.readAllLines(this.getPath(configDefinition, valueContainer)).forEach(line -> {
			if (line.startsWith("!") || line.startsWith("#")) return;

			String[] split = line.split("=", 2);

			values.put(split[0], split[1]);
		});

		return SemanticVersion.parse(values.get("version"));
	}

	@Override
	public String getExtension() {
		return "properties";
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
