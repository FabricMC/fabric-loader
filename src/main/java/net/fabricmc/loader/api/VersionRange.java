package net.fabricmc.loader.api;

import java.util.Objects;

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
			case SAME_MAJOR:
			case SAME_MAJOR_AND_MINOR:
				String[] parts = version.split("\\.");
				int start = 1;
				if (type == Type.SAME_MAJOR_AND_MINOR)
					start = 2;
				for (int i = start; i < parts.length; i++)
					parts[i] = "x";
				return "version " + String.join(".", parts);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VersionRange that = (VersionRange) o;
		return type == that.type &&
				version.equals(that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, version);
	}
}
