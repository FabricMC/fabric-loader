package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

public interface ModDependency {
	String getModId();
	boolean matches(Version version);
}
