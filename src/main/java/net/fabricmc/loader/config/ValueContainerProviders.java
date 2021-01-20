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
			ConfigManager.LOGGER.warn("Attempted to get player value container from root provider.");

			return null;
		}

		@Override
		public int playerCount() {
			return -1;
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
