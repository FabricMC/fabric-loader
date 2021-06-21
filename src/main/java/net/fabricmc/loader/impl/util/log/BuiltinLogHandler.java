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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Queue;

import net.fabricmc.loader.impl.util.SystemProperties;

/**
 * Default LogHandler until Log is initialized.
 *
 * <p>The log handler has the following properties:
 * - log to stdout for anything but LogLevel.ERROR
 * - log to stderr for LogLevel.ERROR
 * - option to relay previous log output to another log handler if requested through Log.init
 * - dumps previous log output to a log file if not closed/relayed yet
 */
final class BuiltinLogHandler extends ConsoleLogHandler {
	private static final String DEFAULT_LOG_FILE = "fabricloader.log";

	private final Queue<ReplayEntry> replayBuffer = new ArrayDeque<>();
	private final Thread shutdownHook;

	BuiltinLogHandler() {
		shutdownHook = new ShutdownHook();
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isReplayedBuiltin) {
		super.log(time, level, category, msg, exc, isReplayedBuiltin);

		synchronized (this) {
			replayBuffer.add(new ReplayEntry(time, level, category, msg, exc));
		}
	}

	@Override
	public void close() {
		Thread shutdownHook = this.shutdownHook;

		if (shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException e) {
				// ignore
			}
		}
	}

	synchronized boolean replay(LogHandler target) {
		ReplayEntry entry;

		while ((entry = replayBuffer.poll()) != null) {
			target.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true);
		}

		return true;
	}

	private static final class ReplayEntry {
		ReplayEntry(long time, LogLevel level, LogCategory category, String msg, Throwable exc) {
			this.time = time;
			this.level = level;
			this.category = category;
			this.msg = msg;
			this.exc = exc;
		}

		final long time;
		final LogLevel level;
		final LogCategory category;
		final String msg;
		final Throwable exc;
	}

	private final class ShutdownHook extends Thread {
		ShutdownHook() {
			super("BuiltinLogHandler shutdown hook");
		}

		@Override
		public void run() {
			synchronized (BuiltinLogHandler.this) {
				if (replayBuffer.isEmpty()) return;

				String fileName = System.getProperty(SystemProperties.LOG_FILE, DEFAULT_LOG_FILE);
				if (fileName.isEmpty()) return;

				try {
					Path file = Paths.get(fileName).toAbsolutePath().normalize();
					Files.createDirectories(file.getParent());

					try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
						ReplayEntry entry;

						while ((entry = replayBuffer.poll()) != null) {
							writer.write(formatLog(entry.time, entry.level, entry.category, entry.msg, entry.exc));
						}
					}
				} catch (IOException e) {
					System.err.printf("Error saving log: %s", e);
				}
			}
		}
	}
}
