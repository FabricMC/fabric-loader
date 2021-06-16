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

package net.fabricmc.loader.impl.metadata;

final class ParseWarning {
	private final int line;
	private final int column;
	private final String key;
	private final String reason;

	ParseWarning(int line, int column, String key) {
		this(line, column, key, null);
	}

	ParseWarning(int line, int column, String key, /* @Nullable */ String reason) {
		this.line = line;
		this.column = column;
		this.key = key;
		this.reason = reason;
	}

	public int getLine() {
		return this.line;
	}

	public int getColumn() {
		return this.column;
	}

	public String getKey() {
		return this.key;
	}

	public String getReason() {
		return this.reason;
	}
}
