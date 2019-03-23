package net.fabricmc.api.settings.schema;

public class NumberConstraint<T extends Number> extends Constraint<T> {

	public NumberConstraint(Restrictions type, T value) {
		super(type, value);
		if (!type.isNumerical()) {
			throw new IllegalArgumentException("type must be numerical");
		}
	}

}
