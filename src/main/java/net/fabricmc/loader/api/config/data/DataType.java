package net.fabricmc.loader.api.config.data;

import net.fabricmc.loader.api.config.value.ConfigValue;
import net.fabricmc.loader.config.Identifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Data represent data that is attached to a specific config value.
 * See {@link ConfigValue#getData(DataType)}.
 */
public class DataType<T> extends Identifiable {
	public static final DataType<String> COMMENT = new DataType<>("fabric", "comment");

	public DataType(@NotNull String namespace, @NotNull String name) {
		super(namespace, name);
	}
}
