package net.fabricmc.api.settings.constraint;

public enum Constraints {
	NUMERICAL_LOWER_BOUND(true, identifier("min")),
	NUMERICAL_UPPER_BOUND(true, identifier("max")),
	STRING_MINIMUM_LENGTH(true, identifier("min_length")),
	STRING_MAXIMUM_LENGTH(true, identifier("max_length")),
	STRING_STARTING_WITH(false, identifier("starts_with")),
	STRING_ENDING_WITH(false, identifier("ends_with")),
	FINAL(false, identifier("final"));

	private final boolean numerical;
	private final Identifier identifier;

	Constraints(boolean numerical, Identifier identifier) {
		this.numerical = numerical;
		this.identifier = identifier;
	}

	private static Identifier identifier(String name) {
		return new Identifier("fabric", name);
	}

	public boolean isNumerical() {
		return numerical;
	}

	public Identifier getIdentifier() {
		return identifier;
	}

	public static class Identifier {
		String domain;
		String name;

		public Identifier(String domain, String name) {
			this.domain = domain;
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public String getDomain() {
			return domain;
		}

		@Override
		public String toString() {
			return getDomain() + ":" + getName();
		}
	}

}
