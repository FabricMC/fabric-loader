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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

class MenuPlatformTest {
	@Test
	void windowsUsesSeparateArgumentsForPathsAndUrls() {
		String path = "C:\\Users\\Ilyas\\My instance\\mods";
		assertArrayEquals(new String[] { "explorer.exe", path }, MenuPlatform.command("Windows 11", path, true));
		String url = "https://example.org/issues?q=a&label=bug";
		assertArrayEquals(new String[] { "rundll32.exe", "url.dll,FileProtocolHandler", url }, MenuPlatform.command("Windows 10", url, false));
	}

	@Test
	void unixOpenersKeepTheTargetAsOneArgument() {
		assertArrayEquals(new String[] { "/usr/bin/open", "/Users/Ilyas/My instance/mods" },
				MenuPlatform.command("Mac OS X", "/Users/Ilyas/My instance/mods", true));
		assertArrayEquals(new String[] { "xdg-open", "https://example.org" }, MenuPlatform.command("Linux", "https://example.org", false));
		assertArrayEquals(new String[] { "/usr/bin/open", "https://example.org" }, MenuPlatform.command("Darwin", "https://example.org", false));
	}

	@Test
	void onlyWebLinksCanLaunchAHandler() throws URISyntaxException {
		assertEquals("https://example.org", MenuPlatform.webUri("https://example.org").toString());
		assertEquals("http://localhost:3000/issues", MenuPlatform.webUri("http://localhost:3000/issues").toString());
		assertNull(MenuPlatform.webUri("file:///C:/Windows"));
		assertNull(MenuPlatform.webUri("javascript:alert(1)"));
		assertNull(MenuPlatform.webUri("https:example.org"));
	}
}
