package net.fabricmc.loader.api.info;

import org.jetbrains.annotations.Nullable;

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

	/**
	 * Create a child progress bar.
	 */
	ProgressBar progressBar(String name, int steps);

	@Nullable
	ProgressBar getParent();
}
