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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.impl.util.log.LogCategory;

/**
 * Minimal bridge/emulation for {@link org.apache.logging.log4j.LogManager}.
 *
 * @deprecated Only for bridging purposes, don't use otherwise!
 */
@Deprecated
public final class LogManager {
	private static final Map<String, Logger> LOGGER_MAP = new ConcurrentHashMap<>();

	public static Logger getLogger(String name) {
		return LOGGER_MAP.computeIfAbsent(name, LogManager::createLogger);
	}

	private static Logger createLogger(String name) {
		LogCategory category;

		switch (name) {
		case "mixin":
			category = LogCategory.MIXIN;
			break;
		default:
			category = new LogCategory("unknown", name);
		}

		return new LoggerImpl(category);
	}
}
