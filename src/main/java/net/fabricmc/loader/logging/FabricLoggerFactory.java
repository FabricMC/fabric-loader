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

package net.fabricmc.loader.logging;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class FabricLoggerFactory {
	private static MethodHandle loggerFactory;

	private FabricLoggerFactory() {

	}

	public static FabricLogger create(String prefix) {
		if (loggerFactory == null) {
			String className = "FabricLoggerStdout";

			try {
				if (Class.forName("org.apache.logging.log4j.LogManager") != null) {
					className = "FabricLoggerLog4j";
				}
			} catch (ClassNotFoundException e) {
				// pass
			}

			try {
				Class loggerClass = Class.forName("net.fabricmc.loader.logging." + className);
				loggerFactory = MethodHandles.publicLookup().unreflectConstructor(loggerClass.getConstructor(String.class));
			} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			return (FabricLogger) loggerFactory.invoke(prefix);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
