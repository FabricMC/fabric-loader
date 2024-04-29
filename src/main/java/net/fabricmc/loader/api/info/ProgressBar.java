package net.fabricmc.loader.api.info;

import java.io.Closeable;

/**
 * A progress bar with 0 is an indeterminate progress bar which can only be completed.
 */
public interface ProgressBar extends Closeable {
	void increment();
	float progress();

	void set(int steps);
	String title();
	void title(String title);
	@Override
	void close();
}
