package net.fabricmc.loader.api.config.value;

import java.util.UUID;

public interface PlayerValueContainer extends ValueContainer {
	UUID getPlayer();
}
