package net.fabricmc.api.settings.constraint;

import net.fabricmc.api.settings.SettingBuilder;

import java.util.List;

public class ConstraintsBuilder<S, T> extends AbstractConstraintsBuilder<T> {

	final SettingBuilder<S, T> source;

	public ConstraintsBuilder(List<Constraint> sourceConstraints, Class<T> type, SettingBuilder<S, T> source) {
		super(sourceConstraints, type);
		this.source = source;
	}

	public CompositeConstraintBuilder<S, T> composite() {
		return new CompositeConstraintBuilder<>(sourceConstraints, type, this);
	}

	public ConstraintsBuilder<S, T> min(T min) {
		addNumericalLowerBound(min);
		return this;
	}

	public ConstraintsBuilder<S, T> max(T min) {
		addNumericalUpperBound(min);
		return this;
	}

	public SettingBuilder<S, T> finish() {
		super.addConstraints();
		return source;
	}

}
