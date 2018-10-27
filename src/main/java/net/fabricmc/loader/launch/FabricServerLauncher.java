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

package net.fabricmc.loader.launch;

import net.minecraft.launchwrapper.Launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FabricServerLauncher {

	public static void main(String[] args) {

		List<String> argList = new ArrayList<>();
		for (String arg : args) {
			argList.add(arg);
		}
		argList.add("--tweakClass");
		argList.add("net.fabricmc.loader.launch.FabricServerTweaker");

		Object[] objectList = argList.toArray();
		String[] stringArray = Arrays.copyOf(objectList, objectList.length, String[].class);
		Launch.main(stringArray);
	}

}
