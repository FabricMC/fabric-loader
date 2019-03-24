package net.fabricmc.api.settings.constraint;

import java.util.List;

public final class CompositeConstraintBuilder<S, T> extends AbstractConstraintsBuilder<T> {

	private final ConstraintsBuilder<S, T> source;
	private final CompositeType compositeType;

	public CompositeConstraintBuilder(CompositeType compositeType, List<Constraint> sourceConstraints, Class<T> type, ConstraintsBuilder<S, T> source) {
		super(sourceConstraints, type);
		this.source = source;
		this.compositeType = compositeType;
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
		addConstraints();
		return source;
	}

	@Override
	void addConstraints() {
		switch (compositeType) {
			case OR:
				sourceConstraints.add(new OrCompositeConstraint(newConstraints));
				break;
			case AND:
				sourceConstraints.add(new AndCompositeConstraint(newConstraints));
				break;
			case INVERT:
				sourceConstraints.add(new InvertCompositeConstraint(newConstraints));
				break;
		}
	}

	abstract class AbstractCompositeConstraint<T> extends ValuedConstraint<String, T> {

		protected final List<Constraint> constraints;

		public AbstractCompositeConstraint(CompositeType type, List<Constraint> constraints) {
			super(Constraints.COMPOSITE, type.getName());
			this.constraints = constraints;
		}

	}

	private class AndCompositeConstraint<T> extends AbstractCompositeConstraint<T> {

		public AndCompositeConstraint(List<Constraint> constraints) {
			super(CompositeType.AND, constraints);
		}

		@Override
		public boolean test(T value) {
			return constraints.stream().anyMatch(constraint -> !constraint.test(value));
		}

	}

	private class OrCompositeConstraint<T> extends AbstractCompositeConstraint<T> {

		public OrCompositeConstraint(List<Constraint> constraints) {
			super(CompositeType.OR, constraints);
		}

		@Override
		public boolean test(T value) {
			return constraints.stream().anyMatch(constraint -> constraint.test(value));
		}

	}

	private class InvertCompositeConstraint<T> extends AbstractCompositeConstraint<T> {

		public InvertCompositeConstraint(List<Constraint> constraints) {
			super(CompositeType.INVERT, constraints);
		}

		@Override
		public boolean test(T value) {
			return !constraints.stream().anyMatch(constraint -> !constraint.test(value));
		}

	}

}
