package net.fabricmc.api.settings.constraint;

import java.util.List;

public final class CompositeConstraintBuilder<S, T> extends AbstractConstraintsBuilder<T> {

	private final ConstraintsBuilder<S, T> source;

	public CompositeConstraintBuilder(List<Constraint> sourceConstraints, Class<T> type, ConstraintsBuilder<S, T> source) {
		super(sourceConstraints, type);
		this.source = source;
	}

	public CompositeConstraintBuilder<S, T> min(T min) {
		addNumericalLowerBound(min);
		return this;
	}

	public CompositeConstraintBuilder<S, T> max(T min) {
		addNumericalUpperBound(min);
		return this;
	}

	public ConstraintsBuilder<S, T> finishComposite() {
		super.addConstraints();
		return source;
	}

}
