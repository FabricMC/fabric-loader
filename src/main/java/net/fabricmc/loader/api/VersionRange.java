package net.fabricmc.loader.api;

public class VersionRange {
	public enum Type {
		INVALID,
		ANY,
		EQUALS,
		GREATER_THAN,
		LESSER_THAN,
		GREATER_THAN_OR_EQUAL,
		LESSER_THAN_OR_EQUAL,
	}

	private final Type type;
	private final String version;

	public VersionRange(Type type, String version) {
		this.type = type;
		this.version = version;
	}

	public Type getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}
}
