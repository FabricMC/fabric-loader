package net.fabricmc.loader.impl.info;

import net.fabricmc.loader.api.info.ProgressBar;

import java.io.Serializable;

public class ProgressBarImpl implements ProgressBar, Serializable {
	private String title;
	private int progress;
	private int steps;
	private boolean completed;
	ProgressBarImpl(String title, int steps) {
		this.title = title;
		this.steps = steps;
	}
	@Override
	public void increment() {
		if (this.completed) throw new IllegalStateException("Already closed!");
		progress++;
	}

	@Override
	public float percentage() {
		return (float) progress / steps;
	}

	@Override
	public int steps() {
		return steps;
	}

	@Override
	public int progress() {
		return progress;
	}

	@Override
	public void set(int steps) {
		if (this.completed) throw new IllegalStateException("Already closed!");
		this.progress = steps;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public void title(String title) {
		if (this.completed) throw new IllegalStateException("Already closed!");
		this.title = title;
	}

	@Override
	public void close() {
		this.completed = true;
	}

	@Override
	public boolean isCompleted() {
		return completed;
	}
}
