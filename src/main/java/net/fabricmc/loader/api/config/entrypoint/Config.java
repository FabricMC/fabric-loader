package net.fabricmc.loader.api.config.entrypoint;

import net.fabricmc.loader.api.config.data.DataType;
import net.fabricmc.loader.api.config.exceptions.ConfigValueException;
import net.fabricmc.loader.api.config.util.Array;
import net.fabricmc.loader.api.config.data.Builders;
import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.util.Table;
import net.fabricmc.loader.api.config.value.ConfigValueCollector;
import net.fabricmc.loader.api.config.value.ValueKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public abstract class Config<R> implements ConfigInitializer<R> {
	private final List<ValueKey<?>> valueKeys = new ArrayList<>();

	protected static <T> ValueKey<T> value(Supplier<T> defaultValue) {
		return new ValueKey.Builder<>(defaultValue).build();
	}

	protected static ValueKey<Integer> value(int defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Long> value(long defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Float> value(float defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Double> value(double defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Boolean> value(boolean defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Byte> value(byte defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Short> value(short defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<Character> value(char defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).build();
	}

	protected static ValueKey<String> value(String defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue).with(DataType.COMMENT).build();
	}

	protected static Builders.Number<Integer> builder(int defaultValue) {
		return new Builders.Number<>(() -> defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	protected static Builders.Number<Long> builder(long defaultValue) {
		return new Builders.Number<>(() -> defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	protected static Builders.Number<Float> builder(float defaultValue) {
		return new Builders.Number<>(() -> defaultValue, Float.MIN_VALUE, Float.MAX_VALUE);
	}

	protected static Builders.Number<Double> builder(double defaultValue) {
		return new Builders.Number<>(() -> defaultValue, Double.MIN_VALUE, Double.MAX_VALUE);
	}

	protected static ValueKey.Builder<Boolean> builder(boolean defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue);
	}

	protected static ValueKey.Builder<Byte> builder(byte defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue);
	}

	protected static ValueKey.Builder<Short> builder(short defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue);
	}

	protected static ValueKey.Builder<Character> builder(char defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue);
	}

	protected static ValueKey.Builder<String> builder(String defaultValue) {
		return new ValueKey.Builder<>(() -> defaultValue);
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	protected static <T> ValueKey<Array<T>> array(Supplier<T> defaultValue, T... values) {
		return new ValueKey.Builder<>(() -> new Array<>((Class<T>) defaultValue.get().getClass(), defaultValue, values)).build();
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	protected static <T> ValueKey<Table<T>> table(Supplier<T> defaultValue, Table.Entry<String, T>... values) {
		return new ValueKey.Builder<>(() -> new Table<>((Class<T>) defaultValue.get().getClass(), defaultValue, values)).build();
	}

	@ApiStatus.Internal
	public final void addConfigValues(@NotNull ConfigValueCollector builder) {
		this.process(builder, new String[0], this.getClass());
	}

	private void process(@NotNull ConfigValueCollector builder, @NotNull String[] parent, Class<?> clazz) {
		for (Field field : clazz.getDeclaredFields()) {
			if (field.getType() == ValueKey.class) {
				int modifier = field.getModifiers();

				if (!Modifier.isFinal(modifier)) {
					throw new ConfigValueException("Field " + field.getName() + " is not final!");
				}

				if (!Modifier.isStatic(modifier)) {
					throw new ConfigValueException("Field " + field.getName() + " is not static!");
				}

				if (!Modifier.isPublic(modifier)) {
					throw new ConfigValueException("Field " + field.getName() + " is not public!");
				}

				try {
					ValueKey<?> valueKey = (ValueKey<?>) field.get(null);

					if (valueKey.isInitialized()) {
						throw new ConfigValueException("ConfigKey " + valueKey.toString() + " already registered!");
					}

					String name = name(field.getName());

					if (parent.length > 0) {
						String[] paths = Arrays.copyOfRange(parent, 1, parent.length + 1);
						paths[paths.length - 1] = name;

						builder.addConfigValue(valueKey, parent[0], paths);
					} else {
						builder.addConfigValue(valueKey, name);
					}

					this.valueKeys.add(valueKey);
				} catch (IllegalAccessException e) {
					throw new ConfigValueException("Error reading field " + field.getDeclaringClass().getName() + "." + field.getName());
				}
			}
		}

		Class<?>[] innerClasses = clazz.getDeclaredClasses();

		for (int i = innerClasses.length - 1; i >= 0; --i) {
			String[] nestedParent = Arrays.copyOf(parent, parent.length + 1);
			nestedParent[nestedParent.length - 1] = name(innerClasses[i]);
			process(builder, nestedParent, innerClasses[i]);
		}
	}

	protected ListView<ValueKey<?>> getValues() {
		return new ListView<>(this.valueKeys);
	}

	private static String name(Class<?> clazz) {
		return name(clazz
				.getSimpleName());
	}

	private static String name(String string) {
		return string
				.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
				.replaceAll("([a-z])([A-Z])", "$1_$2")
				.toLowerCase(Locale.ROOT);
	}
}
