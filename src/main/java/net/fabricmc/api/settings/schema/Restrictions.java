package net.fabricmc.api.settings.schema;

public enum Restrictions {
	NUMERICAL_LOWER_BOUND(true),
	NUMERICAL_UPPER_BOUND(true),
	STRING_MINIMUM_LENGTH(true),
	STRING_MAXIMUM_LENGTH(true),
	STRING_STARTING_WITH(false);

	private boolean numerical;

	Restrictions(boolean numerical) {
		this.numerical = numerical;
	}

	public boolean isNumerical() {
		return numerical;
	}
}
