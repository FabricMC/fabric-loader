package net.fabricmc.loader.api.config.entrypoint;

import net.fabricmc.loader.api.config.SaveType;

import java.util.function.Consumer;

/**
 * Interface that games can use to add their own default save types.
 */
public interface ConfigEnvironment {
	void addToRoot(Consumer<SaveType> consumer);
}
