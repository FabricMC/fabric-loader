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

package net.fabricmc.loader.impl.launch.knot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.jar.Manifest;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;

interface KnotClassLoaderInterface {
	@SuppressWarnings("resource")
	static KnotClassLoaderInterface create(boolean useCompatibility, boolean isDevelopment, EnvType envType, GameProvider provider) {
		if (useCompatibility) {
			return new KnotCompatibilityClassLoader(isDevelopment, envType, provider).getDelegate();
		} else {
			return new KnotClassLoader(isDevelopment, envType, provider).getDelegate();
		}
	}

	void initializeTransformers();

	ClassLoader getClassLoader();

	void addCodeSource(Path path);
	void setAllowedPrefixes(Path codeSource, String... prefixes);
	void setValidParentClassPath(Collection<Path> codeSources);

	Manifest getManifest(Path codeSource);

	boolean isClassLoaded(String name);
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

	byte[] getRawClassBytes(String name) throws IOException;
	byte[] getPreMixinClassBytes(String name);
}
