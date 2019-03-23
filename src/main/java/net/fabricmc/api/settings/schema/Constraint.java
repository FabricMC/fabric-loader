package net.fabricmc.api.settings.schema;

import net.fabricmc.api.settings.Setting;

public class Constraint<T> {

	private final Restrictions type;
	private final T value;

	public Constraint(Restrictions type, T value) {
		this.type = type;
		this.value = value;
	}

	public T getValue() {
		return value;
	}

	public Restrictions getType() {
		return type;
	}
}
