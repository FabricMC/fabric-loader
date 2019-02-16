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

package net.fabricmc.loader.launch.common;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.ModContainer;
import net.fabricmc.loader.ModInfo;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MixinLoader extends FabricLoader {
	static {
		LOGGER = LogManager.getFormatterLogger("Fabric|MixinLoader");
	}

	@Override
	protected boolean isPrimaryLoader() {
		return false;
	}

	@Override
	protected void addMod(ModInfo info, File originFile, boolean initialize) {
		ModContainer container = new ModContainer(info, originFile, initialize);
		mods.add(container);
	}
}
