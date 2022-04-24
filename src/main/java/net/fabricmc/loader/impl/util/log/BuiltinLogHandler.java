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
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.impl.util.LoaderUtil;
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

	private boolean configured;
	private boolean enableOutput;
	private List<ReplayEntry> buffer = new ArrayList<>();
	private final Thread shutdownHook;

	BuiltinLogHandler() {
		shutdownHook = new ShutdownHook();
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean fromReplay, boolean wasSuppressed) {
		boolean output;

		synchronized (this) {
			if (enableOutput) {
				output = true;
			} else if (level.isLessThan(LogLevel.ERROR)) {
				output = false;
			} else {
				startOutput();
				output = true;
			}

			if (buffer != null) {
				buffer.add(new ReplayEntry(time, level, category, msg, exc));
			}
		}

		if (output) super.log(time, level, category, msg, exc, fromReplay, wasSuppressed);
	}

	private void startOutput() {
		if (enableOutput) return;

		if (buffer != null) {
			for (int i = 0; i < buffer.size(); i++) { // index based loop to tolerate replay producing log output by itself
				ReplayEntry entry = buffer.get(i);
				super.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true, true);
			}
		}

		enableOutput = true;
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

	synchronized void configure(boolean buffer, boolean output) {
		if (!buffer && !output) throw new IllegalArgumentException("can't both disable buffering and the output");

		if (output) {
			startOutput();
		} else {
			enableOutput = false;
		}

		if (buffer) {
			if (this.buffer == null) this.buffer = new ArrayList<>();
		} else {
			this.buffer = null;
		}

		configured = true;
	}

	synchronized void finishConfig() {
		if (!configured) configure(false, true);
	}

	synchronized boolean replay(LogHandler target) {
		if (buffer == null || buffer.isEmpty()) return false;

		for (int i = 0; i < buffer.size(); i++) { // index based loop to tolerate replay producing log output by itself
			ReplayEntry entry = buffer.get(i);
			target.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true, !enableOutput);
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
				if (buffer == null || buffer.isEmpty()) return;

				if (!enableOutput) {
					enableOutput = true;

					for (int i = 0; i < buffer.size(); i++) { // index based loop to tolerate replay producing log output by itself
						ReplayEntry entry = buffer.get(i);
						BuiltinLogHandler.super.log(entry.time, entry.level, entry.category, entry.msg, entry.exc, true, true);
					}
				}

				String fileName = System.getProperty(SystemProperties.LOG_FILE, DEFAULT_LOG_FILE);
				if (fileName.isEmpty()) return;

				try {
					Path file = LoaderUtil.normalizePath(Paths.get(fileName));
					Files.createDirectories(file.getParent());

					try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
						for (int i = 0; i < buffer.size(); i++) { // index based loop to tolerate replay producing log output by itself
							ReplayEntry entry = buffer.get(i);
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
