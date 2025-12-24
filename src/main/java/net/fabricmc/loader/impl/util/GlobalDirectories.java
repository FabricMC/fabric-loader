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

package net.fabricmc.loader.impl.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import net.fabricmc.api.EnvType;

public abstract class GlobalDirectories {
	abstract Path getGlobalCacheRoot();

	abstract Path getGlobalDataRoot();

	public static GlobalDirectories create(EnvType envType, Path gameDir) {
		switch (envType) {
		case CLIENT:
			return Client.create();
		case SERVER:
			return new Server(gameDir);
		default:
			throw new IllegalStateException();
		}
	}

	abstract static class Client extends GlobalDirectories {
		private final Path cache;
		private final Path data;

		protected Client(Path cache, Path data) {
			this.cache = cache;
			this.data = data;
		}

		@Override
		public Path getGlobalCacheRoot() {
			return cache;
		}

		@Override
		public Path getGlobalDataRoot() {
			return data;
		}

		static Client create() {
			final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

			if (os.contains("win")) {
				return new Windows();
			} else if (os.contains("mac")) {
				return new MacOS();
			}

			// Linux or unknown.
			return new Linux();
		}

		static final class Windows extends Client {
			private Windows() {
				super(getCacheDir(), getDataDir());
			}

			private static Path getCacheDir() {
				return Paths.get(System.getenv("LocalAppData"), "net.fabricmc.loader");
			}

			private static Path getDataDir() {
				return Paths.get(System.getenv("AppData"), "net.fabricmc.loader");
			}
		}

		static final class MacOS extends Client {
			private MacOS() {
				super(getCacheDir(), getDataDir());
			}

			private static Path getCacheDir() {
				return Paths.get(System.getProperty("user.home"), "Library", "Caches", "net.fabricmc.loader");
			}

			private static Path getDataDir() {
				return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "net.fabricmc.loader");
			}
		}

		static final class Linux extends Client {
			private Linux() {
				super(getCacheDir(), getDataDir());
			}

			private static Path getCacheDir() {
				return Paths.get(System.getProperty("user.home"), ".caches", "net.fabricmc.loader");
			}

			private static Path getDataDir() {
				return Paths.get(System.getProperty("user.home"), ".config", "net.fabricmc.loader");
			}
		}
	}

	static final class Server extends GlobalDirectories {
		private final Path gameDir;

		private Server(Path gameDir) {
			this.gameDir = gameDir;
		}

		@Override
		public Path getGlobalCacheRoot() {
			return gameDir.resolve("fabric-global");
		}

		@Override
		public Path getGlobalDataRoot() {
			return gameDir.resolve("cache-global");
		}
	}
}
