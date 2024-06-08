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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ModDirectories;
import net.fabricmc.loader.impl.util.GlobalDirectories;
import net.fabricmc.loader.impl.util.ModDirectoriesImpl;

// Just a basic test to ensure that we can write to these directories
public class ModDirectoriesTests {
	private final byte[] DATA = "Hello World".getBytes(StandardCharsets.UTF_8);

	@Test
	void testModDirectoriesClient() throws IOException {
		Path gameDir = Files.createTempDirectory("loader-test");
		GlobalDirectories globalDirectories = GlobalDirectories.create(EnvType.CLIENT, gameDir);
		ModDirectories directories = new ModDirectoriesImpl("test", gameDir, globalDirectories);

		Files.write(directories.getCacheDir().resolve("test.txt"), DATA);
		Files.write(directories.getGlobalCacheDir().resolve("test.txt"), DATA);
		Files.write(directories.getDataDir().resolve("test.txt"), DATA);
		Files.write(directories.getGlobalDataDir().resolve("test.txt"), DATA);
	}

	@Test
	void testModDirectoriesServer() throws IOException {
		Path gameDir = Files.createTempDirectory("loader-test");
		GlobalDirectories globalDirectories = GlobalDirectories.create(EnvType.SERVER, gameDir);
		ModDirectories directories = new ModDirectoriesImpl("test", gameDir, globalDirectories);

		Files.write(directories.getCacheDir().resolve("test.txt"), DATA);
		Files.write(directories.getGlobalCacheDir().resolve("test.txt"), DATA);
		Files.write(directories.getDataDir().resolve("test.txt"), DATA);
		Files.write(directories.getGlobalDataDir().resolve("test.txt"), DATA);
	}
}
