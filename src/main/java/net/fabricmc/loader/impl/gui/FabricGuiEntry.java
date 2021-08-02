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

package net.fabricmc.loader.impl.gui;

import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/** The main entry point for all fabric-based stuff. */
public final class FabricGuiEntry {
	/** Opens the given {@link FabricStatusTree} in a new swing window.
	 *
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(FabricStatusTree tree) throws Exception {
		GameProvider provider = FabricLoaderImpl.INSTANCE.getGameProvider();

		if (provider == null && LoaderUtil.hasAwtSupport()
				|| provider != null && provider.hasAwtSupport()) {
			FabricMainWindow.open(tree, true);
		} else {
			openForked(tree);
		}
	}

	private static void openForked(FabricStatusTree tree) throws IOException, InterruptedException {
		Path javaBinDir = Paths.get(System.getProperty("java.home"), "bin").toAbsolutePath();
		String[] executables = { "javaw.exe", "java.exe", "java" };
		Path javaPath = null;

		for (String executable : executables) {
			Path path = javaBinDir.resolve(executable);

			if (Files.isRegularFile(path)) {
				javaPath = path;
				break;
			}
		}

		if (javaPath == null) throw new RuntimeException("can't find java executable in "+javaBinDir);

		Path loaderPath = ClasspathModCandidateFinder.getFabricLoaderPath();
		if (loaderPath == null) throw new RuntimeException("can't determine Fabric Loader path");

		Process process = new ProcessBuilder(javaPath.toString(), "-Xmx100M", "-cp", loaderPath.toString(), FabricGuiEntry.class.getName())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
			tree.writeTo(os);
		}

		int rVal = process.waitFor();
		if (rVal != 0) throw new IOException("subprocess exited with code "+rVal);
	}

	public static void main(String[] args) throws Exception {
		FabricStatusTree tree = new FabricStatusTree(new DataInputStream(System.in));
		FabricMainWindow.open(tree, true);
	}

	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayCriticalError(Throwable exception, boolean exitAfter) {
		Log.error(LogCategory.GENERAL, "A critical error occurred", exception);

		GameProvider provider = FabricLoaderImpl.INSTANCE.getGameProvider();

		if (!GraphicsEnvironment.isHeadless() && (provider == null || provider.canOpenErrorGui())) {
			Version loaderVersion = getLoaderVersion();
			String title;

			if (loaderVersion == null) {
				title = "Fabric Loader";
			} else {
				title = "Fabric Loader " + loaderVersion.getFriendlyString();
			}

			FabricStatusTree tree = new FabricStatusTree(title, "Failed to launch!");
			FabricStatusTab crashTab = tree.addTab("Crash");

			addThrowable(crashTab.node, exception, new HashSet<>());

			// Maybe add an "open mods folder" button?
			// or should that be part of the main tree's right-click menu?
			tree.addButton("Exit").makeClose();

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					Log.warn(LogCategory.GENERAL, "Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}

	private static Version getLoaderVersion() {
		ModContainer mod = FabricLoaderImpl.INSTANCE.getModContainer(FabricLoaderImpl.MOD_ID).orElse(null);
		if (mod != null) return mod.getMetadata().getVersion();

		ModCandidate candidate = FabricLoaderImpl.INSTANCE.getModCandidate(FabricLoaderImpl.MOD_ID);
		if (candidate != null) return candidate.getVersion();

		return null;
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
