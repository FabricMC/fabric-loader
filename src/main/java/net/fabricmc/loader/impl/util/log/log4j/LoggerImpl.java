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

package net.fabricmc.loader.impl.util.log.log4j;

import java.util.IdentityHashMap;
import java.util.Map;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogLevel;

@SuppressWarnings("deprecation")
final class LoggerImpl implements Logger {
	private static final Map<Level, LogLevel> LEVEL_MAP = createLevelMap();

	private final LogCategory category;

	LoggerImpl(LogCategory category) {
		this.category = category;
	}

	@Override
	public void log(Level level, String message, Throwable t) {
		if (message == null) message = "null";

		Log.log(translateLevel(level), category, message, t);
	}

	@Override
	public boolean isEnabled(Level level) {
		return Log.shouldLog(translateLevel(level), category);
	}

	private static LogLevel translateLevel(Level level) {
		return LEVEL_MAP.getOrDefault(level, LogLevel.INFO);
	}

	private static Map<Level, LogLevel> createLevelMap() {
		Map<Level, LogLevel> ret = new IdentityHashMap<>(6);

		ret.put(Level.FATAL, LogLevel.ERROR);
		ret.put(Level.ERROR, LogLevel.ERROR);
		ret.put(Level.WARN, LogLevel.WARN);
		ret.put(Level.INFO, LogLevel.INFO);
		ret.put(Level.DEBUG, LogLevel.DEBUG);
		ret.put(Level.TRACE, LogLevel.TRACE);

		return ret;
	}
}
