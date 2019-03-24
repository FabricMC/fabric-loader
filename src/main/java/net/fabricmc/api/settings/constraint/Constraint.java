package net.fabricmc.api.settings.constraint;

public abstract class Constraint<A> { // A is the type of values we're gonna check

	private final Constraints type;

	public Constraint(Constraints type) {
		this.type = type;
	}

	public Constraints getType() {
		return type;
	}

	public abstract boolean test(A value);

}
