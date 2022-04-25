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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogHandler;
import net.fabricmc.loader.impl.util.log.LogLevel;

public final class Slf4jLogHandler implements LogHandler {
	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		Logger logger = getLogger(category);

		switch (level) {
		case ERROR: return logger.isErrorEnabled();
		case WARN: return logger.isWarnEnabled();
		case INFO: return logger.isInfoEnabled();
		case DEBUG: return logger.isDebugEnabled();
		case TRACE: return logger.isTraceEnabled();
		}

		throw new IllegalArgumentException("unknown level: "+level);
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean fromReplay, boolean wasSuppressed) {
		Logger logger = getLogger(category);

		if (msg == null) {
			if (exc == null) return;
			msg = "Exception";
		}

		switch (level) {
		case ERROR:
			if (exc == null) {
				logger.error(msg);
			} else {
				logger.error(msg, exc);
			}

			break;
		case WARN:
			if (exc == null) {
				logger.warn(msg);
			} else {
				logger.warn(msg, exc);
			}

			break;
		case INFO:
			if (exc == null) {
				logger.info(msg);
			} else {
				logger.info(msg, exc);
			}

			break;
		case DEBUG:
			if (exc == null) {
				logger.debug(msg);
			} else {
				logger.debug(msg, exc);
			}

			break;
		case TRACE:
			if (exc == null) {
				logger.trace(msg);
			} else {
				logger.trace(msg, exc);
			}

			break;
		default:
			throw new IllegalArgumentException("unknown level: "+level);
		}
	}

	private static Logger getLogger(LogCategory category) {
		Logger ret = (Logger) category.data;

		if (ret == null) {
			category.data = ret = LoggerFactory.getLogger(category.toString());
		}

		return ret;
	}

	@Override
	public void close() { }
}
