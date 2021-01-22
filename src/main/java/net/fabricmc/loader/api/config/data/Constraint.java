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

import net.fabricmc.loader.config.Identifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows for formal definition of constraints on config values.
 * Constraints are checked at deserialization time and when somebody attempts to update a config value.
 *
 * @param <T> the type of value this constraint can be applied to
 */
public abstract class Constraint<T> extends Identifiable {
	public Constraint(@NotNull String namespace, @NotNull String name) {
		super(namespace, name);
	}

	public abstract boolean passes(T value);

	@Override
	public String toString() {
		return "@" + super.toString();
	}
}
