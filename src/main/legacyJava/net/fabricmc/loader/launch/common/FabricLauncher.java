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

package net.fabricmc.loader.launch.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import net.fabricmc.api.EnvType;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public interface FabricLauncher {
	void propose(URL url);
	EnvType getEnvironmentType();
	boolean isClassLoaded(String name);
	InputStream getResourceAsStream(String name);
	ClassLoader getTargetClassLoader();
	byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;
	boolean isDevelopment();
	Collection<URL> getLoadTimeDependencies();
}
