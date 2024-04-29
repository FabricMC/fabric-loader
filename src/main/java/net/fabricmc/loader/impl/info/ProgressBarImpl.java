package net.fabricmc.loader.impl.info;

import net.fabricmc.loader.api.info.ProgressBar;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProgressBarImpl implements ProgressBar, Serializable {
	private String title;
	private int progress;
	private int steps;
	private boolean completed;
	private ProgressBarImpl parent;
	// TODO: Are there any concurrency issues?
	private List<ProgressBarImpl> children = new ArrayList<>();
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
		if (this.completed) throw new IllegalStateException("Already closed!");
		this.completed = true;
		this.children.forEach(progressBar -> {
			if (!progressBar.isCompleted()) progressBar.close();
		});
	}

	@Override
	public boolean isCompleted() {
		return completed;
	}

	@Override
	public ProgressBar progressBar(String name, int steps) {
		ProgressBarImpl progressBar = new ProgressBarImpl(name, steps);
		progressBar.parent = this;
		this.children.add(progressBar);
		return progressBar;
	}

	@Override
	public @Nullable ProgressBar getParent() {
		return parent;
	}
}
