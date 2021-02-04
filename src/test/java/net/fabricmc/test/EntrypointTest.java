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

package net.fabricmc.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;

public final class EntrypointTest {

	private static final Logger LOGGER = LogManager.getLogger();
	public static final ModInitializer FIELD_ENTRY = EntrypointTest::fieldEntry;

	public static void staticEntry() {
		LOGGER.info("Static entry called");
	}

	public EntrypointTest() {
		LOGGER.info("EntrypointTest instance created");
	}

	public void instanceEntry() {
		LOGGER.info("Instance entry called");
	}

	public static void fieldEntry() {
		LOGGER.info("Field entry called");
	}
}
