package net.fabricmc.loader.minecraft;

import java.util.OptionalInt;

public final class McVersion {
	/**
	 * The raw version, such as {@code 18w21a}.
	 */
	private final String raw;
	/**
	 * The normalized version.
	 *
	 * <p>This is usually compliant with Semver and
	 * contains release and pre-release information.
	 */
	private final String normalized;
	private final OptionalInt classVersion;

	private McVersion(String name, String release, OptionalInt classVersion) {
		this.raw = name;
		this.normalized = McVersionLookup.normalizeVersion(name, release);
		this.classVersion = classVersion;
	}

	public String getRaw() {
		return this.raw;
	}

	public String getNormalized() {
		return this.normalized;
	}

	public OptionalInt getClassVersion() {
		return this.classVersion;
	}

	@Override
	public String toString() {
		return "McVersion{raw=" + this.raw + ", normalized=" + this.normalized + ", classVersion=" + this.classVersion + "}";
	}

	public static final class Builder {
		private String name;
		private String release;
		private OptionalInt classVersion = OptionalInt.empty();

		// Setters
		public Builder setName(String name) {
			this.name = name;
			return this;
		}

		public Builder setRelease(String release) {
			this.release = release;
			return this;
		}

		public Builder setClassVersion(int classVersion) {
			this.classVersion = OptionalInt.of(classVersion);
			return this;
		}

		// Complex setters
		public Builder setNameAndRelease(String name) {
			return this
				.setName(name)
				.setRelease(McVersionLookup.getRelease(name));
		}

		public Builder setFromFileName(String name) {
			// strip extension
			int pos = name.lastIndexOf('.');
			if (pos > 0) name = name.substring(0, pos);

			return this.setNameAndRelease(name);
		}

		public McVersion build() {
			return new McVersion(this.name, this.release, this.classVersion);
		}
	}
}
