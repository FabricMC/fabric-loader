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

package net.fabricmc.loader.util.log;

import java.io.PrintWriter;
import java.io.StringWriter;

final class ConsoleLogHandler implements LogHandler {
	private static final LogLevel MIN_STDERR_LEVEL = LogLevel.ERROR;
	private static final LogLevel MIN_STDOUT_LEVEL = LogLevel.getDefault();

	ConsoleLogHandler() { }

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc) {
		String formatted = String.format("[%tT] [%s] [%s/%s]: %s%n", time, level.name(), Log.NAME, category.name, msg);

		if (exc != null) {
			StringWriter writer = new StringWriter(formatted.length() + 500);

			try (PrintWriter pw = new PrintWriter(writer, false)) {
				pw.print(formatted);
				exc.printStackTrace(pw);
			}

			formatted = writer.toString();
		}

		if (level.isLessThan(MIN_STDERR_LEVEL)) {
			System.out.print(formatted);
		} else {
			System.err.print(formatted);
		}
	}

	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return !level.isLessThan(MIN_STDOUT_LEVEL);
	}

	@Override
	public void close() { }
}
