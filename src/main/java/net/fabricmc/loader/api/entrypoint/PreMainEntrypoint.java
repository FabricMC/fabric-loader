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

package net.fabricmc.loader.api.entrypoint;

/**
 * Entrypoint getting invoked just before launching the game.
 *
 * <p><b>Avoid interfering with the game from this!</b> Accessing anything needs careful consideration to avoid
 * interfering with its own initialization or otherwise harming its state.
 *
 * <p>The entrypoint is exposed as {@code preMain} in the mod json and runs for any environment. It usually executes
 * several seconds before the main/client/server entrypoints.
 */
@FunctionalInterface
public interface PreMainEntrypoint {
	void onPreMain();
}
