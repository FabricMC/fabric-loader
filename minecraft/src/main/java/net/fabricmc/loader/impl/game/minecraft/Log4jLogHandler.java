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

package net.fabricmc.loader.impl.game.minecraft;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogHandler;
import net.fabricmc.loader.impl.util.log.LogLevel;

public final class Log4jLogHandler implements LogHandler {
	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return getLogger(category).isEnabled(translateLogLevel(level));
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isReplayedBuiltin) {
		// TODO: suppress console log output if isReplayedBuiltin is true to avoid duplicate output
		getLogger(category).log(translateLogLevel(level), msg, exc);
	}

	private static Logger getLogger(LogCategory category) {
		Logger ret = (Logger) category.data;

		if (ret == null) {
			String name = category.name.isEmpty() ? Log.NAME : String.format("%s/%s", Log.NAME, category.name);
			category.data = ret = LogManager.getLogger(name);
		}

		return ret;
	}

	private static Level translateLogLevel(LogLevel level) {
		switch (level) {
		case ERROR: return Level.ERROR;
		case WARN: return Level.WARN;
		case INFO: return Level.INFO;
		case DEBUG: return Level.DEBUG;
		case TRACE: return Level.TRACE;
		}

		throw new IllegalArgumentException("unknown log level: "+level);
	}

	@Override
	public void close() { }
}
