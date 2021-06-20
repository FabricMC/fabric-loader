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

import java.util.Locale;

import net.fabricmc.loader.impl.util.SystemProperties;

public enum LogLevel {
	ERROR, WARN, INFO, DEBUG, TRACE;

	public boolean isLessThan(LogLevel level) {
		return ordinal() > level.ordinal();
	}

	public static LogLevel getDefault() {
		String val = System.getProperty(SystemProperties.LOG_LEVEL);
		if (val == null) return INFO;

		LogLevel ret = LogLevel.valueOf(val.toUpperCase(Locale.ENGLISH));
		if (ret == null) throw new IllegalArgumentException("invalid log level: "+val);

		return ret;
	}
}
