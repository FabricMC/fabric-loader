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

package net.fabricmc.loader.api;

/**
 * Represents an exception that arises when obtaining entrypoints.
 *
 * @see FabricLoader#getEntrypointContainers(String, Class)
 */
@SuppressWarnings("serial")
public class EntrypointException extends RuntimeException {
	private final String key;

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(String key, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "'!", cause);
		this.key = key;
	}

	/**
	 * @deprecated For internal use only, use regular exceptions!
	 */
	@Deprecated
	public EntrypointException(String key, String causingMod, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod + "'", cause);
		this.key = key;
	}

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(String s) {
		super(s);
		this.key = "";
	}

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(Throwable t) {
		super(t);
		this.key = "";
	}

	/**
	 * Returns the key of entrypoint in which the exception arose.
	 *
	 * @return the key
	 */
	public String getKey() {
		return key;
	}
}
