package net.fabricmc.loader.api.metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public interface ContactInformation {
	static final ContactInformation EMPTY = new ContactInformation() {
		@Override
		public Optional<String> get(String key) {
			return Optional.empty();
		}

		@Override
		public Map<String, String> asMap() {
			return Collections.emptyMap();
		}
	};

	Optional<String> get(String key);
	Map<String, String> asMap();
}
