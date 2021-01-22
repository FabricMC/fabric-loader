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

package net.fabricmc.loader.api.config;

import net.fabricmc.loader.api.config.value.ValueContainerProvider;

import java.util.function.Function;

/**
 * This is called after configs have been (de)serialized for the first time.
 *
 * <p>This is the appropriate entrypoint to use to call {@link ValueContainerProvider#register(Function)}.</p>
 *
 * <p>The entrypoint is exposed with {@code configsLoaded} key in the mod json and runs for any environment. It is run
 * immediately before any {@link net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint}s.</p>
 */
public interface ConfigsLoadedEntrypoint {
	void onConfigsLoaded();
}
