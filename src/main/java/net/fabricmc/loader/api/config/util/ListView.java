package net.fabricmc.loader.api.config.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class ListView<T> implements Iterable<T> {
	private final List<T> list;

	public ListView(List<T> list) {
		this.list = list;
	}

	public ListView(Collection<T> collection) {
		this.list = new ArrayList<>(collection);
	}

	public int size() {
		return this.list.size();
	}

	public boolean isEmpty() {
		return this.list.isEmpty();
	}

	public T get(int index) {
		return this.list.get(index);
	}

	@NotNull
	@Override
	public Iterator<T> iterator() {
		return this.list.iterator();
	}
}
