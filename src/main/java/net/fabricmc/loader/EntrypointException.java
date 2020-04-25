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

package net.fabricmc.loader;

import net.fabricmc.loader.api.ModContainer;

public class EntrypointException extends net.fabricmc.loader.api.EntrypointException {
	private static final long serialVersionUID = 6814744471275218006L;
	private final String key;
	private final ModContainer cause;

	public EntrypointException(String key, ModContainer causingMod, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod.getMetadata().getId() + '\'', cause);

		this.key = key;
		this.cause = causingMod;
	}

	@Override
	public String getEntrypointName() {
		return key;
	}

	@Override
	public ModContainer getOwningMod() {
		return cause;
	}
}