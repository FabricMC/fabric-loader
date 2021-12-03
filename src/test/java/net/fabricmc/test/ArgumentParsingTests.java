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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.impl.util.Arguments;

public class ArgumentParsingTests {
	@Test
	public void parseNormal() {
		Arguments arguments = new Arguments();
		arguments.parse(new String[]{"--clientId", "123", "--xuid", "abc", "--versionType", "release"});
		arguments.put("versionType", "Fabric");

		Assertions.assertEquals(arguments.keys().size(), 3);
		Assertions.assertEquals(arguments.get("xuid"), "abc");
		Assertions.assertEquals(arguments.get("versionType"), "Fabric");
	}

	@Test
	public void parseMissing() {
		Arguments arguments = new Arguments();
		arguments.parse(new String[]{"--clientId", "123", "--xuid", "--versionType", "release"});
		arguments.put("versionType", "Fabric");

		Assertions.assertEquals(arguments.keys().size(), 3);
		Assertions.assertEquals(arguments.get("xuid"), "");
		Assertions.assertEquals(arguments.get("versionType"), "Fabric");
	}
}
