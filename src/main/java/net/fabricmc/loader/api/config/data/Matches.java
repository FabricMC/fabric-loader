package net.fabricmc.loader.api.config.data;

public class Matches extends Constraint<String> {
	private final String regex;
	private final String string;

	public Matches(String regex) {
		super("fabric:matches");
		this.regex = regex;
		this.string = "matches \"" + this.regex + '"';
	}

	@Override
	public String toString() {
		return this.string;
	}

	@Override
	public boolean passes(String value) {
		return value.matches(this.regex);
	}
}
