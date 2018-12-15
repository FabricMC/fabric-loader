package net.fabricmc.loader.launch.common;

import net.fabricmc.api.EnvType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

public interface FabricLauncher {
	void propose(URL url);

	Collection<URL> getClasspathURLs();

	EnvType getEnvironmentType();

	boolean isClassLoaded(String name);

	InputStream getResourceAsStream(String name);

	ClassLoader getTargetClassLoader();

	byte[] getClassByteArray(String name) throws IOException;

	boolean isDevelopment();
}
