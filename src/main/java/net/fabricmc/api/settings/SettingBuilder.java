package net.fabricmc.api.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SettingBuilder<S, T> {

	Class<T> type;

	T value;
	String comment = "";
	private List<BiConsumer<T, T>> consumers = new ArrayList<>();
	private List<Function<T, Boolean>> restrictions = new ArrayList<>();
	private String name;
	private Converter<S, T> converter;
	private Settings registry;

	public SettingBuilder(Settings registry, Class<T> type) {
		this.registry = registry;
		this.type = type;
	}

	/**
	 * Attempts to create a copy of given SettingBuilder. Will attempt to cast everything.
	 */
	protected SettingBuilder(SettingBuilder<S, Object> copy, Class<T> type) {
		this(copy.registry, type);
		this.value = (T) copy.value;
		this.comment = copy.comment;
		this.consumers = copy.consumers.stream().map(consumer -> (BiConsumer<T, T>) consumer::accept).collect(Collectors.toList());
		this.name = copy.name;
		this.converter = (Converter<S, T>) copy.converter;
	}

	public <A> SettingBuilder type(Class<? extends A> clazz) {
		return new SettingBuilder(this, clazz);
	}

	public SettingBuilder<S, T> comment(String comment) {
		if (!this.comment.isEmpty()) this.comment += "\n";
		this.comment += comment;
		return this;
	}

	public SettingBuilder<S, T> listen(BiConsumer<T, T> consumer) {
		consumers.add(consumer);
		return this;
	}

	public SettingBuilder<S, T> name(String name) {
		this.name = name;
		return this;
	}

	public SettingBuilder<S, T> defaultValue(T value) {
		this.value = value;
		return this;
	}

	public SettingBuilder<S, T> converter(Converter<S, T> converter) {
		this.converter = converter;
		return this;
	}

	public SettingBuilder<S, T> restrict(Function<T, Boolean> restriction) {
		this.restrictions.add(restriction);
		return this;
	}

	public Setting<T> build() {
		return registerAndSet(new Setting<>(comment, name, (a, b) -> consumers.forEach(consumer -> consumer.accept(a, b)), restriction(), value, type, converter == null ? registry.provideConverter(type) : converter));
	}

	private Setting<T> registerAndSet(Setting<T> setting) {
		if (setting.getName() != null) {
			registry.registerAndRecover(setting);
		}
		return setting;
	}

	protected Predicate<T> restriction() {
		return restrictions.isEmpty() ? t -> false : t -> restrictions.stream().anyMatch(function -> !function.apply(t));
	}
}
