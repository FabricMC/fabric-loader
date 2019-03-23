package net.fabricmc.api.settings;

import java.util.function.BiConsumer;

public class Setting<T> {

	private final String comment;
	private final String name;
	private final BiConsumer<T, T> consumer;
	private T value;

	private Class<T> type;

	public Setting(String comment, String name, BiConsumer<T, T> consumer, T value, Class<T> type) {
		this.comment = comment;
		this.name = name;
		this.consumer = consumer;
		this.value = value;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public T getValue() {
		return value;
	}

	public void setValue(T value) {
		this.consumer.accept(this.value, value);
		this.value = value;
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

}