package net.fabricmc.loader.config;

import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Identifiable {
	private final String id;

	public Identifiable(@NotNull String namespace, @NotNull String name) {
		this.id = namespace + ":" + name;

		if (!isValid(namespace)) {
			throw new ConfigIdentifierException("Non [a-z0-9_.-/] character in namespace: " + this.id);
		} else if (!isValid(name)) {
			throw new ConfigIdentifierException("Non [a-z0-9_.-/] character in name: " + this.id);
		}
	}

	@Override
	public String toString() {
		return this.id;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Identifiable that = (Identifiable) o;
		return id.equals(that.id);
	}

	@Override
	public final int hashCode() {
		return Objects.hash(id);
	}

	private static boolean isValid(String string) {
		for(int i = 0; i < string.length(); ++i) {
			if (!isCharacterValid(string.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isCharacterValid(char c) {
		return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '/';
	}
}
