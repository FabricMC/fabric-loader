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

import java.util.Arrays;
import java.util.IllegalFormatException;

public final class Log {
	public static final String NAME = "FabricLoader";
	private static final boolean CHECK_FOR_BRACKETS = true;

	private static LogHandler handler = new BuiltinLogHandler();

	public static void init(LogHandler handler, boolean replayBuiltin) {
		if (handler == null) throw new NullPointerException("null log handler");

		LogHandler oldhHandler = Log.handler;

		if (oldhHandler instanceof BuiltinLogHandler && replayBuiltin) {
			((BuiltinLogHandler) oldhHandler).replay(handler);
		}

		Log.handler = handler;
		oldhHandler.close();
	}

	public static void error(LogCategory category, String format, Object... args) {
		logFormat(LogLevel.ERROR, category, format, args);
	}

	public static void error(LogCategory category, String msg) {
		log(LogLevel.ERROR, category, msg);
	}

	public static void error(LogCategory category, String msg, Throwable exc) {
		log(LogLevel.ERROR, category, msg, exc);
	}

	public static void warn(LogCategory category, String format, Object... args) {
		logFormat(LogLevel.WARN, category, format, args);
	}

	public static void warn(LogCategory category, String msg) {
		log(LogLevel.WARN, category, msg);
	}

	public static void warn(LogCategory category, String msg, Throwable exc) {
		log(LogLevel.WARN, category, msg, exc);
	}

	public static void info(LogCategory category, String format, Object... args) {
		logFormat(LogLevel.INFO, category, format, args);
	}

	public static void info(LogCategory category, String msg) {
		log(LogLevel.INFO, category, msg);
	}

	public static void info(LogCategory category, String msg, Throwable exc) {
		log(LogLevel.INFO, category, msg, exc);
	}

	public static void debug(LogCategory category, String format, Object... args) {
		logFormat(LogLevel.DEBUG, category, format, args);
	}

	public static void debug(LogCategory category, String msg) {
		log(LogLevel.DEBUG, category, msg);
	}

	public static void debug(LogCategory category, String msg, Throwable exc) {
		log(LogLevel.DEBUG, category, msg, exc);
	}

	public static void trace(LogCategory category, String format, Object... args) {
		logFormat(LogLevel.TRACE, category, format, args);
	}

	public static void trace(LogCategory category, String msg) {
		log(LogLevel.TRACE, category, msg);
	}

	public static void trace(LogCategory category, String msg, Throwable exc) {
		log(LogLevel.TRACE, category, msg, exc);
	}

	public static void log(LogLevel level, LogCategory category, String msg) {
		log(handler, level, category, msg, null);
	}

	public static void log(LogLevel level, LogCategory category, String msg, Throwable exc) {
		log(handler, level, category, msg, exc);
	}

	public static void logFormat(LogLevel level, LogCategory category, String format, Object... args) {
		LogHandler handler = Log.handler;
		if (!handler.shouldLog(level, category)) return;

		String msg;
		Throwable exc;

		if (args.length == 0) {
			//assert getRequiredArgs(format.toString()) == 0;

			msg = format;
			exc = null;
		} else {
			if (CHECK_FOR_BRACKETS) {
				if (format.indexOf("{}") != -1) throw new IllegalArgumentException("log message containing {}: "+format);
			}

			Object lastArg = args[args.length - 1];
			Object[] newArgs;

			if (lastArg instanceof Throwable && getRequiredArgs(format) < args.length) {
				exc = (Throwable) lastArg;
				newArgs = Arrays.copyOf(args, args.length - 1);
			} else {
				exc = null;
				newArgs = args;
			}

			assert getRequiredArgs(format) == newArgs.length;

			try {
				msg = String.format(format, newArgs);
			} catch (IllegalFormatException e) {
				msg = "Format error: fmt=["+format+"] args="+Arrays.toString(args);
				warn(LogCategory.LOG, "Invalid format string.", e);
			}
		}

		log(handler, level, category, msg, exc);
	}

	private static int getRequiredArgs(String format) {
		int ret = 0;
		int minRet = 0;
		boolean wasPct = false;

		for (int i = 0, max = format.length(); i < max; i++) {
			char c = format.charAt(i);

			if (c == '%') {
				wasPct = !wasPct;
			} else if (wasPct) {
				wasPct = false;

				if (c == 'n' || c == '<') { // not %n or %<x
					continue;
				}

				if (c >= '0' && c <= '9') { // abs indexing %12$
					int start = i;

					while (i + 1 < format.length()
							&& (c = format.charAt(i + 1)) >= '0' && c <= '9') {
						i++;
					}

					if (i + 1 < format.length() && format.charAt(i + 1) == '$') {
						i++;
						minRet = Math.max(minRet, Integer.parseInt(format.substring(start, i)) + 1);
						continue;
					} else {
						i = start;
					}
				}

				ret++;
			}
		}

		return Math.max(ret, minRet);
	}

	private static void log(LogHandler handler, LogLevel level, LogCategory category, String msg, Throwable exc) {
		handler.log(System.currentTimeMillis(), level, category, msg.trim(), exc, false);
	}

	public static boolean shouldLog(LogLevel level, LogCategory category) {
		return handler.shouldLog(level, category);
	}
}
