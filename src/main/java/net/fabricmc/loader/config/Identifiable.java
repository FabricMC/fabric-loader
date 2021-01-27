/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.config;

import net.fabricmc.loader.api.config.exceptions.ConfigIdentifierException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

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

	public void addStrings(Consumer<String> stringConsumer) {
		stringConsumer.accept(this.toString());
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
