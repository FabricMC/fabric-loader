package net.fabricmc.loader.api.config.entrypoint;

import net.fabricmc.loader.api.config.SaveType;

import java.util.function.Consumer;

public interface ConfigEnvironment {
	void addToRoot(Consumer<SaveType> consumer);
}
