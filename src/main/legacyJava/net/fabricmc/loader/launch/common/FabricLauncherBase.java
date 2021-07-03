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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.util.UrlUtil;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public class FabricLauncherBase implements FabricLauncher {
	private final net.fabricmc.loader.impl.launch.FabricLauncher parent = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher();

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	public static FabricLauncher getLauncher() {
		return new FabricLauncherBase();
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return new MappingConfiguration();
	}

	@Override
	public void propose(URL url) {
		try {
			parent.addToClassPath(UrlUtil.asPath(url));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public EnvType getEnvironmentType() {
		return FabricLoader.getInstance().getEnvironmentType();
	}

	@Override
	public boolean isClassLoaded(String name) {
		return parent.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return parent.getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return parent.getTargetClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		return parent.getClassByteArray(name, runTransformers);
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		return parent.getLoadTimeDependencies();
	}
}
