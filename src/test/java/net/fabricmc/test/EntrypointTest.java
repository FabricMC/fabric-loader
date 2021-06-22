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

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class EntrypointTest {
	public static final CustomEntry FIELD_ENTRY = EntrypointTest::fieldEntry;

	public static String staticEntry() {
		return "static";
	}

	public EntrypointTest() {
		Log.info(LogCategory.TEST, "EntrypointTest instance created");
	}

	public String instanceEntry() {
		return "instance";
	}

	public static String fieldEntry() {
		return "field";
	}
}
