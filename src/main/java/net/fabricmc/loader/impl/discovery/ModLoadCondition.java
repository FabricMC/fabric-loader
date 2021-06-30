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

/**
 * Conditions for whether to load a mod.
 *
 * <p>These apply only after the unique ID and nesting requirements are met. If a mod ID is shared by multiple mod
 * candidates only one of them will load. Mods nested within another mod only load if the encompassing mod loads.
 *
 * <p>Each condition encompasses all conditions after it in enum declaration order. For example {@link #IF_POSSIBLE}
 * also loads if the conditions for {@link #IF_RECOMMENDED} or {@link #IF_NEEDED} are met.
 */
public enum ModLoadCondition {
	/**
	 * Load always, triggering an error if that is not possible.
	 *
	 * <p>This is the default for root mods (typically those in the mods folder).
	 */
	ALWAYS,
	/**
	 * Load whenever there is nothing contradicting.
	 *
	 * <p>This is the default for nested mods.
	 */
	IF_POSSIBLE,
	/**
	 * Load if the mod is recommended by another loading mod.
	 */
	IF_RECOMMENDED,
	/**
	 * Load if another loading mod requires the mod.
	 */
	IF_NEEDED;
}
