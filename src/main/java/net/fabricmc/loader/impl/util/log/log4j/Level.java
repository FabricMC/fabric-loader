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

/**
 * Minimal bridge/emulation for {@link org.apache.logging.log4j.Level}.
 *
 * @deprecated Only for bridging purposes, don't use otherwise!
 */
@Deprecated
public final class Level {
	public static final Level FATAL = new Level();
	public static final Level ERROR = new Level();
	public static final Level WARN = new Level();
	public static final Level INFO = new Level();
	public static final Level DEBUG = new Level();
	public static final Level TRACE = new Level();
}
