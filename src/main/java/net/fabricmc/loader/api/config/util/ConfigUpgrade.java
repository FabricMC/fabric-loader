package net.fabricmc.loader.api.config.util;

import net.fabricmc.loader.api.SemanticVersion;

@FunctionalInterface
public interface ConfigUpgrade<R> {
	/**
	 * @param from the version represented in the representation
	 * @param representation the intermediate representation of the existing config file
	 * @return whether or not to try to deserialize the existing config file after upgrading
	 */
	boolean upgrade(SemanticVersion from, R representation);
}
