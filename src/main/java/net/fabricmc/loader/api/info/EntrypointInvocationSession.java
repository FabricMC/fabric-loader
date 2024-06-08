package net.fabricmc.loader.api.info;

import net.fabricmc.loader.api.ModContainer;

import java.io.Closeable;

public interface EntrypointInvocationSession extends Closeable {
	/**
	 * Called <b>before</b> an entrypoint is invoked.
	 */
	void preInvoke(ModContainer mod, int index, int size);
	Throwable error(ModContainer mod, Throwable throwable, int index, int size);
	@Override
	void close();
}
