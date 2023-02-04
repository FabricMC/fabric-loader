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

package net.fabricmc.loader.impl.junit;

import org.junit.platform.launcher.LauncherInterceptor;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public class FabricLoaderLauncherInterceptor implements LauncherInterceptor {
	static {
		System.setProperty("fabric.development", "true");
	}

	private final Knot knot;
	private final ClassLoader classLoader;

	public FabricLoaderLauncherInterceptor() {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		try {
			knot = new Knot(EnvType.CLIENT);
			classLoader = knot.init(new String[]{});
		} finally {
			// Knot.init sets the context class loader, revert it back for now.
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public <T> T intercept(Invocation<T> invocation) {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		currentThread.setContextClassLoader(classLoader);

		try {
			return invocation.proceed();
		} finally {
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public void close() {
		// TODO close knot?
	}
}
