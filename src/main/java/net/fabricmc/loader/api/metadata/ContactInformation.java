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

package net.fabricmc.loader.api.metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a contact information.
 */
public interface ContactInformation {
	/**
	 * An empty contact information.
	 */
	ContactInformation EMPTY = new ContactInformation() {
		@Override
		public Optional<String> get(String key) {
			return Optional.empty();
		}

		@Override
		public Map<String, String> asMap() {
			return Collections.emptyMap();
		}
	};

	/**
	 * Gets a certain type of contact information.
	 *
	 * @param key the type of contact information
	 * @return an optional contact information
	 */
	Optional<String> get(String key);

	/**
	 * Gets all contact information provided as a map from contact type to information.
	 */
	Map<String, String> asMap();
}
