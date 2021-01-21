package net.fabricmc.loader.api.config.data;

import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.config.Identifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Flags represent a property of a specific config value.
 * A present flag is treated as 'true', while an absent flag is treated as 'false'.
 * See {@link ConfigValue#isFlagSet(Flag)}.
 */
public final class Flag extends Identifiable {
	public Flag(@NotNull String namespace, @NotNull String name) {
		super(namespace, name);
	}

	@Override
	public String toString() {
		return "$" + super.toString();
	}
}
