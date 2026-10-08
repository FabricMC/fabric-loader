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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

class ModListModelTest {
	@Test
	void sortingUsesIdToBreakNameTiesAndIncludesNestedMods() {
		ModListModel model = new ModListModel(Arrays.asList(mod("z", "Library"), mod("a", "Library"), mod("b", "Alpha")));
		List<ModMetadata> entries = model.filter("");
		assertEquals("b", entries.get(0).getId());
		assertEquals("a", entries.get(1).getId());
		assertEquals("z", entries.get(2).getId());
	}

	@Test
	void filteringIsIndependentOfSystemLocaleAndMatchesIds() {
		Locale previous = Locale.getDefault();

		try {
			Locale.setDefault(new Locale("tr", "TR"));
			ModListModel model = new ModListModel(Arrays.asList(mod("fabric-api", "Fabric API"), mod("sodium", "Sodium")));
			assertEquals(1, model.filter("  FABRIC-API  ").size());
			assertEquals(1, model.filter("SODIUM").size());
			assertTrue(model.filter("missing").isEmpty());
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void snapshotAndFilterResultsCannotChangeSubsequentQueries() {
		List<ModContainer> source = new ArrayList<>(Collections.singletonList(mod("one", "One")));
		ModListModel model = new ModListModel(source);
		source.clear();
		model.filter("").clear();
		assertEquals(1, model.size());
		assertEquals(1, model.filter("").size());
		assertTrue(new ModListModel(Collections.emptyList()).filter("").isEmpty());
	}

	private static ModContainer mod(String id, String name) {
		ModContainer container = mock(ModContainer.class);
		ModMetadata metadata = mock(ModMetadata.class);
		when(container.getMetadata()).thenReturn(metadata);
		when(metadata.getId()).thenReturn(id);
		when(metadata.getName()).thenReturn(name);
		return container;
	}
}
