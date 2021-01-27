package net.fabricmc.loader.api.config.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Table<T> extends StronglyTypedImmutableCollection<Integer, T, Table.Entry<String, T>> {
	private final Entry<String, T>[] values;
	private final Map<String, T> valueMap = new HashMap<>();

	@SafeVarargs
	public Table(Class<T> valueClass, Supplier<T> defaultValue, Entry<String, T>... values) {
		super(valueClass, defaultValue);
		this.values = values;

		for (Entry<String, T> entry : values) {
			this.valueMap.put(entry.key, entry.value);
		}
	}

	public Table(Table<T> table) {
		super(table.valueClass, table.defaultValue);
		this.values = Arrays.copyOf(table.values, table.values.length);
	}

	@Override
	public Table<T> addEntry() {
		//noinspection unchecked
		Entry<String, T>[] values = (Entry<String, T>[]) Array.newInstance(Entry.class, this.values.length + 1);

		for (int i = 0; i < this.values.length; ++i) {
			values[i] = new Entry<>(this.values[i].key, this.values[i].value);
		}

		values[values.length - 1] = new Entry<>("", this.defaultValue.get());

		return new Table<>(this.valueClass, this.defaultValue, values);
	}

	@Override
	public T get(Integer key) {
		return this.values[key].value;
	}

	public T get(String key) {
		return this.valueMap.get(key);
	}

	@Override
	public Table<T> set(Integer key, T value) {
		//noinspection unchecked
		Entry<String, T>[] values = (Entry<String, T>[]) Array.newInstance(Entry.class, this.values.length);

		for (int i = 0; i < this.values.length; ++i) {
			values[i] = new Entry<>(this.values[i].key, i == key ? value : this.values[i].value);
		}

		return new Table<>(this.valueClass, this.defaultValue, values);
	}

	@Override
	public Table<T> remove(int index) {
		//noinspection unchecked
		Entry<String, T>[] values = (Entry<String, T>[]) Array.newInstance(Entry.class, this.values.length - 1);

		for (int i = 0; i < this.values.length - 1; ++i) {
			values[i] = this.values[i >= index ? i + 1 : i];
		}

		return new Table<>(this.valueClass, this.defaultValue, values);
	}

	@Override
	public int size() {
		return this.values.length;
	}

	@NotNull
	@Override
	public Iterator<Entry<String, T>> iterator() {
		return Arrays.asList(this.values).iterator();
	}

	@Override
	public Iterable<T> getValues() {
		return Arrays.stream(this.values).map(e -> e.value).collect(Collectors.toList());
	}

	public Table<T> setKey(int key, String name) {
		//noinspection unchecked
		Entry<String, T>[] values = (Entry<String, T>[]) Array.newInstance(Entry.class, this.values.length);

		for (int i = 0; i < this.values.length; ++i) {
			values[i] = new Entry<>(i == key ? name : this.values[i].key, this.values[i].value);
		}

		return new Table<>(this.valueClass, this.defaultValue, values);

	}

	public static class Entry<K, V> {
		private K key;
		private V value;

		public Entry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		public K getKey() {
			return key;
		}

		public void setKey(K key) {
			this.key = key;
		}

		public V getValue() {
			return value;
		}

		public void setValue(V value) {
			this.value = value;
		}
	}
}
