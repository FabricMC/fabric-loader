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

import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.game.GameProvider;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusTab;

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

	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayCriticalError(Throwable exception, boolean exitAfter) {
		FabricLoader.INSTANCE.getLogger().fatal("A critical error occurred", exception);

		GameProvider provider = FabricLoader.INSTANCE.getGameProvider();

		if ((provider == null || provider.canOpenErrorGui()) && !GraphicsEnvironment.isHeadless()) {
			FabricStatusTree tree = new FabricStatusTree();
			FabricStatusTab crashTab = tree.addTab("Crash");

			tree.mainText = "Failed to launch!";
			addThrowable(crashTab.node, exception, new HashSet<>());

			// Maybe add an "open mods folder" button?
			// or should that be part of the main tree's right-click menu?
			tree.addButton("Exit").makeClose();

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					FabricLoader.INSTANCE.getLogger().warn("Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}

	private static void addThrowable(FabricStatusNode node, Throwable e, Set<Throwable> seen) {
		if (!seen.add(e)) {
			return;
		}

		// Remove some self-repeating exception traces from the tree
		// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
		Throwable cause;

		while ((cause = e.getCause()) != null) {
			if (e.getSuppressed().length > 0) {
				break;
			}

			String msg = e.getMessage();

			if (msg == null) {
				msg = e.getClass().getName();
			}

			if (!msg.equals(cause.getMessage()) && !msg.equals(cause.toString())) {
				break;
			}

			e = cause;
		}

		FabricStatusNode sub = node.addException(e);

		if (e.getCause() != null) {
			addThrowable(sub, e.getCause(), seen);
		}

		for (Throwable t : e.getSuppressed()) {
			addThrowable(sub, t, seen);
		}
	}
}
