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

package net.fabricmc.loader.impl.game.minecraft.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

final class ModListModel {
	private final List<ModMetadata> mods;

	ModListModel(Collection<ModContainer> containers) {
		List<ModMetadata> snapshot = new ArrayList<>();

		for (ModContainer container : containers) {
			snapshot.add(container.getMetadata());
		}

		snapshot.sort(Comparator.comparing((ModMetadata mod) -> mod.getName().toLowerCase(Locale.ROOT))
				.thenComparing(ModMetadata::getId));
		mods = Collections.unmodifiableList(snapshot);
	}

	List<ModMetadata> filter(String query) {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		List<ModMetadata> result = new ArrayList<>();

		for (ModMetadata mod : mods) {
			if (mod.getName().toLowerCase(Locale.ROOT).contains(needle)
					|| mod.getId().toLowerCase(Locale.ROOT).contains(needle)) {
				result.add(mod);
			}
		}

		return result;
	}

	int size() {
		return mods.size();
	}
}
