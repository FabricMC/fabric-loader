package net.fabricmc.loader.api.info;

public interface Message {
	void pin();
	void unpin();

	/**
	 * Sets a new message. Should only be used when pinned.
	 */
	void title(String message);
}
