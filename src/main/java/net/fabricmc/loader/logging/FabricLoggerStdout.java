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

public class FabricLoggerStdout extends FabricLogger {
	public FabricLoggerStdout(String prefix) {
		super(prefix);
	}

	@Override
	protected boolean shouldPrint(Severity severity) {
		return severity != Severity.TRACE; // TODO
	}

	@Override
	protected void internalPrint(Severity severity, String message) {
		String msg = "[" + severity.name() + ":" + prefix + "] " + message;

		if (severity == Severity.WARN || severity == Severity.ERROR) {
			System.err.println(msg);
		} else {
			System.out.println(msg);
		}
	}

	@Override
	protected void internalPrint(Severity severity, String message, Throwable e) {
		internalPrint(severity, message);
		e.printStackTrace((severity == Severity.WARN || severity == Severity.ERROR) ? System.err : System.out);
	}
}
