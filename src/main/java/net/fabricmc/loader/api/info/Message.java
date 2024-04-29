package net.fabricmc.loader.api.info;

import java.io.Closeable;

public interface Message extends Closeable {
	boolean pinned();
	/**
	 * Sets a new message. Should only be used when pinned.
	 */
	void title(String message);
	String title();
	@Override
	void close();
}
