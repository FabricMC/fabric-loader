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
		SAME_MAJOR,
		SAME_MAJOR_AND_MINOR
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

	@Override
	public String toString() {
		switch (type) {
			default:
			case INVALID:
				return "unknown version";
			case ANY:
				return "any version";
			case EQUALS:
				return "version " + version;
			case GREATER_THAN:
				return "any version after " + version;
			case LESSER_THAN:
				return "any version before " + version;
			case GREATER_THAN_OR_EQUAL:
				return "version " + version + " or later";
			case LESSER_THAN_OR_EQUAL:
				return "version " + version + " or earlier";
			// TODO improve these two
			case SAME_MAJOR:
				return "any version that shares major component with " + version;
			case SAME_MAJOR_AND_MINOR:
				return "any version that shares major and minor components with " + version;
		}
	}
}
