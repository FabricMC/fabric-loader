package net.fabricmc.loader.impl.info;

import net.fabricmc.loader.api.info.Message;

import java.io.Serializable;

class MessageImpl implements Message, Serializable {
	private boolean closed;
	private String title;

	MessageImpl(String title) {
		this.title = title;
	}

	@Override
	public boolean pinned() {
		return !closed;
	}

	@Override
	public void title(String message) {
		if (closed) throw new IllegalStateException("Already closed!");
		title = message;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public void close() {
		if (closed) throw new IllegalStateException("Already closed!");
		closed = true;
	}
}
