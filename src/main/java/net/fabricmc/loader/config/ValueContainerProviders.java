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

import net.fabricmc.loader.api.config.SaveType;
import net.fabricmc.loader.api.config.value.ValueContainer;
import net.fabricmc.loader.api.config.value.ValueContainerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class ValueContainerProviders {
	private static final Collection<Function<@NotNull SaveType, @Nullable ValueContainerProvider>> PROVIDER_PROVIDERS = new HashSet<>();

	private static final ValueContainerProvider ROOT = new ValueContainerProvider() {
		@Override
		public ValueContainer getValueContainer() {
			return ValueContainer.ROOT;
		}

		@Override
		public ValueContainer getPlayerValueContainer(UUID playerId) {
			ConfigManagerImpl.LOGGER.warn("Attempted to get player value container from root provider.");
			ConfigManagerImpl.LOGGER.warn("Returning root config value container.");

			return ValueContainer.ROOT;
		}

		@Override
		public @NotNull Iterator<Map.Entry<UUID, ValueContainer>> iterator() {
			return Collections.emptyIterator();
		}
	};

	public static @NotNull ValueContainerProvider getInstance(SaveType saveType) {
		if (saveType == SaveType.ROOT) return ROOT;

		for (Function<@NotNull SaveType, @Nullable ValueContainerProvider> providerProvider : PROVIDER_PROVIDERS) {
			ValueContainerProvider provider = providerProvider.apply(saveType);

			if (provider != null) return provider;
		}

		return ROOT;
	}

	public static void register(Function<@NotNull SaveType, @Nullable ValueContainerProvider> providerProvider) {
		PROVIDER_PROVIDERS.add(providerProvider);
	}
}
