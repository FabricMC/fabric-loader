package net.fabricmc.loader.api.config.serialization.toml;

import java.util.*;

public class TomlElement implements Iterable<String> {
    private final Object object;
    private final List<String> comments = new ArrayList<>();

    public TomlElement(Object object, Collection<String> comments) {
        this.object = object;
        this.comments.addAll(comments);
    }

    public TomlElement(Object object, String... comments) {
        this(object, Arrays.asList(comments));
    }

	public Object getObject() {
        return this.object;
    }

    @Override
    public Iterator<String> iterator() {
        return this.comments.iterator();
    }

    public boolean hasComments() {
    	return !this.comments.isEmpty();
	}
}
