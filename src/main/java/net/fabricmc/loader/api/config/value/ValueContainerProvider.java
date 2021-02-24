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

package net.fabricmc.loader.api.config.value;

import net.fabricmc.loader.api.config.data.SaveType;
import net.fabricmc.loader.config.ValueContainerProviders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Allows other libraries or mods to add config value containers
 */
public interface ValueContainerProvider {
	/**
	 * Registers a provider provider
	 *
	 * @param providerProvider takes in the configs defined save type and returns either a
	 * 						   {@link ValueContainerProvider} if it fits the circumstances or null, if not.
	 */
	static void register(Function<@NotNull SaveType, @Nullable ValueContainerProvider> providerProvider) {
		ValueContainerProviders.register(providerProvider);
	}

	ValueContainer getValueContainer();
    ValueContainer getPlayerValueContainer(UUID playerId);

	@NotNull Iterator<Map.Entry<UUID, ValueContainer>> iterator();
}
