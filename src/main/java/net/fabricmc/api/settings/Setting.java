package net.fabricmc.api.settings;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class Setting<T> {

	private final String comment;
	private final String name;
	private final BiConsumer<T, T> consumer;
	private final Function<T, Boolean> restriction;
	private T value;

	private Class<T> type;
	private Converter<?, T> converter;

	public Setting(String comment, String name, BiConsumer<T, T> consumer, Function<T, Boolean> restriction, T value, Class<T> type, Converter<?, T> converter) {
		this.comment = comment;
		this.name = name;
		this.consumer = consumer;
		this.restriction = restriction;
		this.value = value;
		this.type = type;
		this.converter = converter;
	}

	public String getName() {
		return name;
	}

	public T getValue() {
		return value;
	}

	public boolean setValue(T value) {
		if (restriction.apply(value)) return false;
		this.value = value;
		this.consumer.accept(value, this.value);
		return true;
	}

	public BiConsumer<T, T> getConsumer() {
		return consumer;
	}

	public String getComment() {
		return comment;
	}

	public Class<T> getType() {
		return type;
	}

	public boolean hasComment() {
		return !comment.isEmpty();
	}

	<S> Converter<S, T> getConverter() {
		return (Converter<S, T>) converter;
	}
}