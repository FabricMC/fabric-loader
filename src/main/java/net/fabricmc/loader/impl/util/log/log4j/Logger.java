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

import java.util.Arrays;
import java.util.Objects;

/**
 * Minimal bridge/emulation for {@link org.apache.logging.log4j.Logger}.
 *
 * @deprecated Only for bridging purposes, don't use otherwise!
 */
@Deprecated
public interface Logger {
	default void catching(Throwable t) {
		catching(Level.ERROR, t);
	}

	default void catching(Level level, Throwable t) {
		log(level, "Catching", t);
	}

	default void error(Object message) {
		log(Level.ERROR, message);
	}

	default void error(String message) {
		log(Level.ERROR, message);
	}

	default void error(String message, Object... params) {
		log(Level.ERROR, message, params);
	}

	default void error(String message, Throwable t) {
		log(Level.ERROR, message, t);
	}

	default void warn(Object message) {
		log(Level.WARN, message);
	}

	default void warn(String message) {
		log(Level.WARN, message);
	}

	default void warn(String message, Object... params) {
		log(Level.WARN, message, params);
	}

	default void warn(String message, Throwable t) {
		log(Level.WARN, message, t);
	}

	default void info(Object message) {
		log(Level.INFO, message);
	}

	default void info(String message) {
		log(Level.INFO, message);
	}

	default void info(String message, Object... params) {
		log(Level.INFO, message, params);
	}

	default void info(String message, Throwable t) {
		log(Level.INFO, message, t);
	}

	default void debug(Object message) {
		log(Level.DEBUG, message);
	}

	default void debug(String message) {
		log(Level.DEBUG, message);
	}

	default void debug(String message, Object... params) {
		log(Level.DEBUG, message, params);
	}

	default void debug(String message, Throwable t) {
		log(Level.DEBUG, message, t);
	}

	default void trace(Object message) {
		log(Level.TRACE, message);
	}

	default void trace(String message) {
		log(Level.TRACE, message);
	}

	default void trace(String message, Object... params) {
		log(Level.TRACE, message, params);
	}

	default void trace(String message, Throwable t) {
		log(Level.TRACE, message, t);
	}

	default void log(Level level, Object message) {
		log(level, Objects.toString(message), (Throwable) null);
	}

	default void log(Level level, String message) {
		log(level, message, (Throwable) null);
	}

	default void log(Level level, String message, Object... params) {
		if (!isEnabled(level)) return;
		Throwable exc = null;

		if (params != null && params.length > 0) {
			if (message == null) {
				if (params[0] instanceof Throwable) exc = (Throwable) params[0];
			} else {
				// emulate Log4J's {} tokens and \ escapes
				StringBuilder sb = new StringBuilder(message.length() + 20);
				int paramIdx = 0;
				boolean escaped = false;

				for (int i = 0, max = message.length(); i < max; i++) {
					char c = message.charAt(i);

					if (escaped) {
						sb.append(c);
						escaped = false;
					} else if (c == '\\' && i + 1 < max) {
						escaped = true;
					} else if (c == '{' && i + 1 < max && message.charAt(i + 1) == '}' && paramIdx < params.length) { // unescaped {} with matching param idx
						Object param = params[paramIdx++];

						if (param == null) {
							sb.append("null");
						} else if (param.getClass().isArray()) {
							String val = Arrays.deepToString(new Object[] { param });
							sb.append(val, 1, val.length() - 1);
						} else {
							sb.append(param);
						}
					} else {
						sb.append(c);
					}
				}

				message = sb.toString();

				if (paramIdx < params.length && params[params.length - 1] instanceof Throwable) {
					exc = (Throwable) params[params.length - 1];
				}
			}
		}

		log(level, message, exc);
	}

	void log(Level level, String message, Throwable t);

	boolean isEnabled(Level level);
}
