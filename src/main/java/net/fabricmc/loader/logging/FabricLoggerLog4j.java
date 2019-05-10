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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FabricLoggerLog4j extends FabricLogger {
	private final Logger logger;

	public FabricLoggerLog4j(String prefix) {
		super(prefix);
		this.logger = LogManager.getFormatterLogger(prefix);
	}

	@Override
	protected boolean shouldPrint(Severity severity) {
		switch (severity) {
			case INFO:
				return logger.isInfoEnabled();
			case WARN:
				return logger.isWarnEnabled();
			case ERROR:
				return logger.isErrorEnabled();
			case DEBUG:
				return logger.isDebugEnabled();
			case TRACE:
				return logger.isTraceEnabled();
			default:
				return true;
		}
	}

	@Override
	protected void internalPrint(Severity severity, String message) {
		switch (severity) {
			case INFO:
				logger.info(message);
				break;
			case WARN:
				logger.warn(message);
				break;
			case ERROR:
				logger.error(message);
				break;
			case DEBUG:
				logger.debug(message);
				break;
			case TRACE:
				logger.trace(message);
				break;
			default:
				logger.warn("(Unsupported level " + severity.name() + ") " + message);
				break;
		}
	}

	@Override
	protected void internalPrint(Severity severity, String message, Throwable e) {
		switch (severity) {
			case INFO:
				logger.info(message, e);
				break;
			case WARN:
				logger.warn(message, e);
				break;
			case ERROR:
				logger.error(message, e);
				break;
			case DEBUG:
				logger.debug(message, e);
				break;
			case TRACE:
				logger.trace(message, e);
				break;
			default:
				logger.warn("(Unsupported level " + severity.name() + ") " + message, e);
				break;
		}
	}
}
