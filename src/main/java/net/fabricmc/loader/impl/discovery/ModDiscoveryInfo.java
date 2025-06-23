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

package net.fabricmc.loader.impl.discovery;

import java.util.List;

public class ModDiscoveryInfo {
	private final List<ModCandidateImpl> modsFound;
	private final ModResolutionException exception;

	public ModDiscoveryInfo(List<ModCandidateImpl> discoveredMods, ModResolutionException exception) {
		this.modsFound = discoveredMods;
		this.exception = exception;
	}

	public List<ModCandidateImpl> getFoundMods() {
		return modsFound;
	}

	public ModResolutionException getException() {
		return exception;
	}

	public boolean launchable() {
		return exception == null;
	}
}
