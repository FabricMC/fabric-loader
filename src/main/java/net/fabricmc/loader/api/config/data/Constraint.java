package net.fabricmc.loader.api.config.data;

import net.fabricmc.loader.config.Identifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows for formal definition of constraints on config values.
 * Constraints are checked at deserialization time and when somebody attempts to update a config value.
 *
 * @param <T> the type of value this constraint can be applied to
 */
public abstract class Constraint<T> extends Identifiable {
	public Constraint(@NotNull String namespace, @NotNull String name) {
		super(namespace, name);
	}

	public abstract boolean passes(T value);

	@Override
	public String toString() {
		return "@" + super.toString();
	}
}
