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

package net.fabricmc.loader.impl.util.log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ConsoleLogHandler implements LogHandler {
	private static final LogLevel MIN_STDERR_LEVEL = LogLevel.ERROR;
	private static final LogLevel MIN_STDOUT_LEVEL = LogLevel.getDefault();

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean fromReplay, boolean wasSuppressed) {
		String formatted = formatLog(time, level, category, msg, exc);

		if (level.isLessThan(MIN_STDERR_LEVEL)) {
			System.out.print(formatted);
		} else {
			System.err.print(formatted);
		}
	}

	protected static String formatLog(long time, LogLevel level, LogCategory category, String msg, Throwable exc) {
		String ret = String.format("[%tT] [%s] [%s/%s]: %s%n", time, level.name(), category.context, category.name, msg);

		if (exc != null) {
			StringWriter writer = new StringWriter(ret.length() + 500);

			try (PrintWriter pw = new PrintWriter(writer, false)) {
				pw.print(ret);
				exc.printStackTrace(pw);
			}

			ret = writer.toString();
		}

		return ret;
	}

	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return !level.isLessThan(MIN_STDOUT_LEVEL);
	}

	@Override
	public void close() { }
}
