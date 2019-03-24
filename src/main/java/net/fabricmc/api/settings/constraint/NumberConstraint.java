package net.fabricmc.api.settings.constraint;

import net.fabricmc.api.settings.constraint.Constraint;
import net.fabricmc.api.settings.constraint.Constraints;

import java.math.BigDecimal;

public class NumberConstraint<T extends Number> extends ValuedConstraint<T, T> {

	public NumberConstraint(Constraints type, T value) {
		super(type, value);
		if (!type.isNumerical()) {
			throw new IllegalArgumentException("type must be numerical");
		}
	}

	@Override
	public boolean test(T value) {
		// Sadly, because Number doesn't provide anything to compare numbers, we use BigDecimal instead (to capture all possible number types)
		int compared = new BigDecimal(getValue().toString()).compareTo(new BigDecimal(value.toString()));
		switch (getType()) {
			case NUMERICAL_LOWER_BOUND:
				return compared > 0;
			case NUMERICAL_UPPER_BOUND:
				return compared < 0;
			default:
				throw new IllegalStateException("A NumberConstraint must be of type NUMERICAL_LOWER_BOUND or NUMERICAL_UPPER_BOUND");
		}
	}

}
