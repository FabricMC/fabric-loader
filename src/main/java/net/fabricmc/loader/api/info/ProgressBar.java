package net.fabricmc.loader.api.info;

import java.io.Closeable;

/**
 * A progress bar with 0 is an indeterminate progress bar which can only be completed.
 */
public interface ProgressBar extends Closeable {
	void increment();
	float percentage();
	int progress();

	/**
	 * @return The total amount of steps the progress bar has.
	 */
	int steps();
	void set(int steps);
	String title();
	void title(String title);
	@Override
	void close();

	boolean isCompleted();
}
