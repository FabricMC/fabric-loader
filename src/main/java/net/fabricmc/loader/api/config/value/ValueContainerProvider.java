package net.fabricmc.loader.api.config.value;

import net.fabricmc.loader.api.config.SaveType;
import net.fabricmc.loader.config.ValueContainerProviders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public interface ValueContainerProvider {
	Collection<Function<@NotNull SaveType, @Nullable ValueContainerProvider>> PROVIDER_PROVIDERS = new HashSet<>();

	static void register(Function<@NotNull SaveType, @Nullable ValueContainerProvider> providerProvider) {
		ValueContainerProviders.register(providerProvider);
	}

	ValueContainer getValueContainer();
    ValueContainer getPlayerValueContainer(UUID playerId);
    int playerCount();
    @NotNull Iterator<Map.Entry<UUID, ValueContainer>> iterator();
}
