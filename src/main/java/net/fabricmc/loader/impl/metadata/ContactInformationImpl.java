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

package net.fabricmc.loader.impl.metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.metadata.ContactInformation;

public class ContactInformationImpl implements ContactInformation {
	private final Map<String, String> map;

	public ContactInformationImpl(Map<String, String> map) {
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
