package net.fabricmc.loader.api.config.serialization;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.config.ConfigDefinition;
import net.fabricmc.loader.api.config.ConfigManager;
import net.fabricmc.loader.api.config.ConfigSerializer;
import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigSerializationException;
import net.fabricmc.loader.api.config.serialization.toml.Toml;
import net.fabricmc.loader.api.config.serialization.toml.TomlElement;
import net.fabricmc.loader.api.config.util.Array;
import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.util.Table;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.config.value.ValueKey;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TomlSerializer implements ConfigSerializer<Map<String, TomlElement>> {
	public static final TomlSerializer INSTANCE = new TomlSerializer();

	@SuppressWarnings("rawtypes")
	private final HashMap<Class<?>, ValueSerializer> serializableTypes = new HashMap<>();
	private final HashMap<Class<?>, Function<ValueKey<?>, ValueSerializer<?>>> typeDependentSerializers = new HashMap<>();

	public TomlSerializer() {
		this.addSerializer(Boolean.class, new Direct<>());
		this.addSerializer(Integer.class, new Direct<>());
		this.addSerializer(Long.class, new Direct<>());
		this.addSerializer(String.class, new Direct<>());
		this.addSerializer(Float.class, new Direct<>());
		this.addSerializer(Double.class, new Direct<>());

		this.addSerializer(Array.class, key -> new ArraySerializer(key.getDefaultValue()));
		this.addSerializer(Table.class, key -> new TableSerializer(key.getDefaultValue()));
	}

	/**
	 * @param valueClass the class to be (de)serialized by the specified value serializer
	 * @param valueSerializer the serializer that handles (de)serialization
	 */
	public final <T> void addSerializer(Class<T> valueClass, ValueSerializer<T> valueSerializer) {
		this.serializableTypes.putIfAbsent(valueClass, valueSerializer);

		//noinspection unchecked
		valueClass = (Class<T>) ReflectionUtil.getClass(valueClass);

		for (Class<?> clazz : ReflectionUtil.getClasses(valueClass)) {
			this.serializableTypes.putIfAbsent(clazz, valueSerializer);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public final <T> void addSerializer(Class<T> valueClass, Function<ValueKey<T>, ValueSerializer<T>> serializerBuilder) {
		this.typeDependentSerializers.putIfAbsent(valueClass, (Function) serializerBuilder);
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(Class<V> valueClass) {
		return (ValueSerializer<V>) serializableTypes.get(valueClass);
	}

	@SuppressWarnings("unchecked")
	protected final <V> ValueSerializer<V> getSerializer(ValueKey<V> valueKey) {
		V defaultValue = valueKey.getDefaultValue();

		if (typeDependentSerializers.containsKey(defaultValue.getClass())) {
			return (ValueSerializer<V>) typeDependentSerializers.get(defaultValue.getClass()).apply(valueKey);
		}

		return (ValueSerializer<V>) this.getSerializer(defaultValue.getClass());
	}

	@Override
	public final void serialize(ConfigDefinition<Map<String, TomlElement>> configDefinition, OutputStream outputStream, ValueContainer valueContainer, Predicate<ValueKey<?>> valuePredicate, boolean minimal) throws IOException {
		Map<String, TomlElement> root = new LinkedHashMap<>();

		ListView<String> configComments = configDefinition.getData(DataType.COMMENT);

		Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));

		for (String comment : configComments) {
			for (String c : comment.split("\\n")) {
				writer.write("# " + c + '\n');
			}
		}

		for (ValueKey<?> value : configDefinition) {
			if (valuePredicate.test(value)) {
				doNested(root, value, (key, map) -> {
					Object v = value.getValue(valueContainer);
					Collection<String> comments = minimal
							? Collections.emptyList()
							: ConfigManager.getComments(value);

					map.put(key, new TomlElement(this.getSerializer(value).serializeValue(v), comments));
				});
			}
		}

		this.write(root, writer);
	}

	@Override
	public final void deserialize(ConfigDefinition<Map<String, TomlElement>> configDefinition, InputStream inputStream, ValueContainer valueContainer) throws IOException {
		Map<String, TomlElement> root = this.getRepresentation(inputStream);

		MutableBoolean backup = new MutableBoolean(false);

		for (ValueKey value : configDefinition) {
			doNested(root, value, (key, map) -> {
				ValueSerializer<?> serializer = this.getSerializer(value);
				TomlElement representation = map.get(key);

				if (representation != null) {
					value.setValue(serializer.deserialize(representation.getObject()), valueContainer);
				} else {
					backup.setTrue();
				}
			});
		}

		backup.booleanValue();
	}


	private void doNested(Map<String, TomlElement> root, ValueKey<?> value, BiConsumer<String, Map<String, TomlElement>> consumer) {
		Map<String, TomlElement> object = root;
		String[] path = value.getPath();

		for (int i = 0; i < path.length; ++i) {
			if (i == path.length - 1) {
				consumer.accept(path[i], object);
			} else {
				TomlElement element = object.get(path[i]);
				if (element == null) {
					Map<String, TomlElement> map = new LinkedHashMap<>();
					object.put(path[i], new TomlElement(map));
					object = map;
				} else if (element.getObject() instanceof Map) {
					//noinspection unchecked
					object = (Map<String, TomlElement>) element.getObject();
				} else {
					throw new ConfigSerializationException("Expected Map, got " + element.getObject().getClass());
				}
			}
		}
	}

	protected void write(Map<String, TomlElement> map, Writer writer) throws IOException {
		Toml.write(map, writer);
	}

	@Override
	public @NotNull String getExtension() {
		return "toml";
	}

	@Override
	public @Nullable SemanticVersion getVersion(InputStream inputStream) throws IOException, VersionParsingException {
		return SemanticVersion.parse((String) Toml.read(inputStream).get("version").getObject());
	}

	@Override
	public @NotNull Map<String, TomlElement> getRepresentation(InputStream inputStream) throws IOException {
		return Toml.read(inputStream);
	}

	public interface ValueSerializer<T> {
		Object serialize(T value);

		default Object serializeValue(Object value) {
			//noinspection unchecked
			return this.serialize((T) value);
		}

		T deserialize(Object object);
	}

	private static class Direct<T> implements ValueSerializer<T> {
		@Override
		public Object serialize(T value) {
			return value;
		}

		@Override
		public T deserialize(Object object) {
			//noinspection unchecked
			return (T) object;
		}
	}

	private class ArraySerializer<T> implements ValueSerializer<Array<T>> {
		private final Array<T> defaultValue;

		private ArraySerializer(Array<T> defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object serialize(Array<T> value) {
			Collection array = new ArrayList();
			ValueSerializer<T> serializer = TomlSerializer.this.getSerializer(value.getValueClass());

			for (T t : value) {
				array.add(serializer.serialize(t));
			}

			return array;
		}

		@Override
		public Array<T> deserialize(Object representation) {
			ValueSerializer<T> serializer = TomlSerializer.this.getSerializer(this.defaultValue.getValueClass());

			Collection array = (Collection) representation;

			//noinspection unchecked
			T[] values = (T[]) java.lang.reflect.Array.newInstance(defaultValue.getValueClass(), array.size());

			int i = 0;
			for (Object element : array) {
				values[i++] = serializer.deserialize(element);
			}

			return new Array<>(this.defaultValue.getValueClass(), this.defaultValue.getDefaultValue(), values);
		}
	}

	private class TableSerializer<T> implements ValueSerializer<Table<T>> {
		private final Table<T> defaultValue;

		private TableSerializer(Table<T> defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object serialize(Table<T> table) {
			Map<String, TomlElement> map = new LinkedHashMap<>();
			ValueSerializer<T> serializer = TomlSerializer.this.getSerializer(this.defaultValue.getValueClass());

			for (Table.Entry<String, T>  t : table) {
				map.put(t.getKey(), new TomlElement(serializer.serialize(t.getValue())));
			}

			return map;
		}

		@Override
		public Table<T> deserialize(Object representation) {
			ValueSerializer<T> serializer = TomlSerializer.this.getSerializer(this.defaultValue.getValueClass());

			//noinspection unchecked
			Map<String, TomlElement> map = (Map<String, TomlElement>) representation;

			//noinspection unchecked
			Table.Entry<String, T>[] values = (Table.Entry<String, T>[]) java.lang.reflect.Array.newInstance(Table.Entry.class, map.size());

			int i = 0;
			for (Map.Entry<String, TomlElement> entry : map.entrySet()) {
				values[i++] = new Table.Entry<>(entry.getKey(), serializer.deserialize(entry.getValue().getObject()));
			}

			return new Table<>(this.defaultValue.getValueClass(), this.defaultValue.getDefaultValue(), values);
		}
	}
}
