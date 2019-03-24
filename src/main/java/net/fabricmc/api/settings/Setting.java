package net.fabricmc.api.settings;

import net.fabricmc.api.settings.constraint.Constraint;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class Setting<T> {

	private final String comment;
	private final String name;
	private final BiConsumer<T, T> consumer;
	private final Predicate<T> restriction;
	private T value;

	private Class<T> type;
	private Converter<?, T> converter;

	// Only used when generating a schema
	final List<Constraint> constraintList;

	Setting(String comment, String name, BiConsumer<T, T> consumer, Predicate<T> restriction, T value, Class<T> type, Converter<?, T> converter, List<Constraint> constraintList) {
		this.comment = comment;
		this.name = name;
		this.consumer = consumer;
		this.restriction = restriction;
		this.value = value;
		this.type = type;
		this.converter = converter;
		this.constraintList = constraintList;
	}

	public String getName() {
		return name;
	}

	public T getValue() {
		return value;
	}

	public boolean setValue(T value) {
		if (restriction.test(value)) return false;
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

	public List<Constraint> getConstraintList() {
		return constraintList;
	}

}