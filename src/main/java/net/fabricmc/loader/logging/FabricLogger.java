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

public abstract class FabricLogger {
	protected final String prefix;

	FabricLogger(String prefix) {
		this.prefix = prefix;
	}

	protected abstract boolean shouldPrint(Severity severity);
	protected abstract void internalPrint(Severity severity, String message);
	protected abstract void internalPrint(Severity severity, String message, Throwable e);

	public final void print(Severity severity, String message) {
		if (shouldPrint(severity)) {
			internalPrint(severity, message);
		}
	}

	public final void print(Severity severity, String message, Object... args) {
		if (shouldPrint(severity)) {
			internalPrint(severity, String.format(message, args));
		}
	}

	public final void info(String message, Object... args) {
		print(Severity.INFO, message, args);
	}

	public final void warn(String message, Object... args) {
		print(Severity.WARN, message, args);
	}

	public final void error(String message, Object... args) {
		print(Severity.ERROR, message, args);
	}

	public final void debug(String message, Object... args) {
		print(Severity.DEBUG, message, args);
	}

	public final void trace(String message, Object... args) {
		print(Severity.TRACE, message, args);
	}

	public final void info(String message, Throwable e) {
		if (shouldPrint(Severity.INFO)) {
			internalPrint(Severity.INFO, message, e);
		}
	}

	public final void warn(String message, Throwable e) {
		if (shouldPrint(Severity.WARN)) {
			internalPrint(Severity.WARN, message, e);
		}
	}

	public final void error(String message, Throwable e) {
		if (shouldPrint(Severity.ERROR)) {
			internalPrint(Severity.ERROR, message, e);
		}
	}

	public final void debug(String message, Throwable e) {
		if (shouldPrint(Severity.DEBUG)) {
			internalPrint(Severity.DEBUG, message, e);
		}
	}

	public final void trace(String message, Throwable e) {
		if (shouldPrint(Severity.TRACE)) {
			internalPrint(Severity.TRACE, message, e);
		}
	}

	public enum Severity {
		TRACE,
		DEBUG,
		INFO,
		WARN,
		ERROR
	}
}
