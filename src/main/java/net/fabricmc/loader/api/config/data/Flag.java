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

import net.fabricmc.loader.api.config.value.ValueKey;
import net.fabricmc.loader.config.Identifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Flags represent a property of a specific config value.
 * A present flag is treated as 'true', while an absent flag is treated as 'false'.
 * See {@link ValueKey#isFlagSet(Flag)}.
 */
public final class Flag extends Identifiable {
	public Flag(@NotNull String namespace, @NotNull String name) {
		super(namespace, name);
	}

	@Override
	public String toString() {
		return "$" + super.toString();
	}
}
