package net.fabricmc.loader.api.config;

import net.fabricmc.loader.config.Identifiable;

public final class SaveType extends Identifiable {
	/**
	 * Only loaded when the game first starts. Requires a restart to take effect.
	 */
	public static final SaveType ROOT = new SaveType("fabric", "root");

	public SaveType(String namespace, String name) {
		super(namespace, name);
	}
}
