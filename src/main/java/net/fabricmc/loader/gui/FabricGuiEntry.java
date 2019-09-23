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

package net.fabricmc.loader.gui;

/** The main entry point for all fabric-based stuff. */
public final class FabricGuiEntry {

	/** Opens the given {@link FabricStatusTree} in a new swing window.
	 * 
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(FabricStatusTree tree) throws Exception {
		openWindow(tree, true);
	}

	private static void openWindow(FabricStatusTree tree, boolean shouldWait) throws Exception {
		FabricMainWindow.open(tree, shouldWait);
	}
}
