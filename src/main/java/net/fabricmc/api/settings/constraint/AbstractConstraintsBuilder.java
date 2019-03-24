package net.fabricmc.api.settings.constraint;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractConstraintsBuilder<T> {

	final List<Constraint> sourceConstraints;
	protected final Class<T> type;

	private final List<Constraint> newConstraints = new ArrayList<>();

	AbstractConstraintsBuilder(List<Constraint> sourceConstraints, Class<T> type) {
		this.sourceConstraints = sourceConstraints;
		this.type = type;
	}

	void addNumericalLowerBound(T bound) {
		checkNumerical(bound);
		newConstraints.add(new NumberConstraint<>(Constraints.NUMERICAL_LOWER_BOUND, (Number) bound));
	}

	void addNumericalUpperBound(T bound) {
		checkNumerical(bound);
		newConstraints.add(new NumberConstraint<>(Constraints.NUMERICAL_UPPER_BOUND, (Number) bound));
	}

	private void checkNumerical(T value) {
		if (!Number.class.isAssignableFrom(value.getClass())) throw new IllegalStateException("Can't apply numerical constraint to non-numerical setting");
	}

	void addConstraints() {
		sourceConstraints.addAll(newConstraints);
	}

}
