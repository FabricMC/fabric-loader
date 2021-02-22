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

package net.fabricmc.loader.api.config.data;

import net.fabricmc.loader.api.config.util.ListView;
import net.fabricmc.loader.api.config.util.StronglyTypedImmutableCollection;
import net.fabricmc.loader.api.config.util.Table;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Allows for formal definition of constraints on config values.
 * Constraints are checked at deserialization time and when somebody attempts to update a config value.
 *
 * @param <T> the type of value this constraint can be applied to
 */
public abstract class Constraint<T> {
	protected final String name;

	public Constraint(@NotNull String name) {
		this.name = name;
	}

	/**
	 * Tests a config value against this constraint.
	 *
	 * @param value the value to test
	 * @return whether or not the value passes this constraint
	 */
	public abstract boolean passes(T value);

	/**
	 * Can be overridden to allow constraints to append multiple lines to config files.
	 *
	 * By default will append a single line, the string representation of this constraint.
	 *
	 * @param linesConsumer consumes strings to add to the config file, each string is its own line
	 */
	public void addLines(Consumer<String> linesConsumer) {
		linesConsumer.accept(this.toString());
	}

	/**
	 * Gets the name of the constraint.
	 *
	 * Used when serialized to a config file by default.
	 *
	 * @return the name of this constraint
	 */
	public final String getName() {
		return this.name;
	}

	/**
	 * Returns the string representation of this constraint.
	 *
	 * Can be used to provide more detail in config files.
	 *
	 * @return the string representation of this constraint
	 */
	@Override
	public String toString() {
		return this.name;
	}

	/**
	 * Represents a constraint on elements of a {@link StronglyTypedImmutableCollection}.
	 *
	 * @param <T> the class of the collection
	 * @param <V> the class of the values in the collection
	 */
	public static class Value<T extends StronglyTypedImmutableCollection<?, V, ?>, V> extends Constraint<T> {
		private final List<Constraint<V>> constraints = new ArrayList<>();

		@SafeVarargs
		public Value(@NotNull String name, Constraint<V>... constraints) {
			super(name);
			this.constraints.addAll(Arrays.asList(constraints));
		}

		public Value(@NotNull String name, Collection<Constraint<V>> constraints) {
			super(name);
			this.constraints.addAll(constraints);
		}

		@Override
		public boolean passes(T value) {
			for (V v : value.getValues()) {
				for (Constraint<V> constraint : this.constraints) {
					if (!constraint.passes(v)) return false;
				}
			}

			return true;
		}

		public ListView<Constraint<V>> getConstraints() {
			return new ListView<>(this.constraints);
		}

		@Override
		public void addLines(Consumer<String> linesConsumer) {
			this.constraints.forEach(constraint -> constraint.addLines(linesConsumer));
		}
	}

	/**
	 * Represents a constraint on the keys that are valid for a {@link Table}.
	 *
	 * @param <T> the class of the table in question
	 */
	public static class Key<T extends Table<?>> extends Constraint<T> {
		private final List<Constraint<String>> constraints = new ArrayList<>();

		@SafeVarargs
		public Key(@NotNull String name, Constraint<String>... constraints) {
			super(name);
			this.constraints.addAll(Arrays.asList(constraints));
		}

		public Key(@NotNull String name, Collection<Constraint<String>> constraints) {
			super(name);
			this.constraints.addAll(constraints);
		}

		public ListView<Constraint<String>> getConstraints() {
			return new ListView<>(this.constraints);
		}

		@Override
		public boolean passes(T value) {
			for (Table.Entry<String, ?> entry : value) {
				for (Constraint<String> constraint : this.constraints) {
					if (!constraint.passes(entry.getKey())) return false;
				}
			}

			return true;
		}

		@Override
		public void addLines(Consumer<String> linesConsumer) {
			this.constraints.forEach(constraint -> constraint.addLines(linesConsumer));
		}
	}
}
