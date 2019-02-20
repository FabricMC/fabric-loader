package net.fabricmc.loader.metadata;

import net.fabricmc.loader.api.metadata.ContactInformation;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class MapBackedContactInformation implements ContactInformation {
	private final Map<String, String> map;

	public MapBackedContactInformation(Map<String, String> map) {
		this.map = Collections.unmodifiableMap(map);
	}

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(map.get(key));
	}

	@Override
	public Map<String, String> asMap() {
		return map;
	}
}
