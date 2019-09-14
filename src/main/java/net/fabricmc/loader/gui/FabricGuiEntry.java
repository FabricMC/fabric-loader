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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.gui.FabricStatusTree.FabricTreeWarningLevel;

/** The main entry point for all fabric-based stuff. */
public final class FabricGuiEntry {

	public static final String OPTION_ALWAYS_SHOW_INFO = "fabric_loader.info_gui.always_show";

	/** The entry point for forking the main application over into a different process to get around incompatibilities
	 * on OSX, and to separate the main launch from the swing runtime. (This is only used if no errors are present, but
	 * the user has specified the {@link #OPTION_ALWAYS_SHOW_INFO} flag. */
	public static void main(String[] args) {

		if (args.length == 2 && "--from-tree".equals(args[0])) {
			FabricStatusTree tree = FabricStatusTree.read(args[1]);

			onForkInternal(args, tree);

		} else if (args.length == 2 && "--read-tree".equals(args[0])) {

			File from = new File(args[1]);

			if (!from.isFile()) {
				System.out.println("Not a file! (" + args[1] + ")");
				System.exit(-1);
				return;
			}

			FabricStatusTree tree;
			try (FileInputStream fis = new FileInputStream(from)) {
				try (InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
					tree = FabricStatusTree.read(reader);
				}
			} catch (IOException e) {
				System.out.println("Failed to read from the file");
				System.exit(-1);
				return;
			}

			System.out.println("Status: read from the file.");

			onForkInternal(args, tree);

		} else if (args.length == 1 && ("--test".equals(args[0]) || "--test-fork".equals(args[0]))) {

			// Test code
			FabricStatusTree tree = new FabricStatusTree();
			tree.mainText = "Failed to launch!";

			FabricStatusTab except = tree.addTab("Errors");
			FabricStatusNode exception = except.addChild("Crash");
			exception.setError();
			exception.expandByDefault = true;

			FabricStatusNode node = exception.addChild("Test");
			node.setWarning();
			node.addChild("lols").setInfo();
			node.iconType = FabricStatusTree.ICON_TYPE_JAR_FILE;

			FabricStatusNode jarRoot = exception.addChild("Jars");
			jarRoot.iconType = FabricStatusTree.ICON_TYPE_FOLDER;

			for (int i = 0; i < 8; i++) {
				boolean isFabric = i >= 4;
				FabricStatusNode jarNode = jarRoot.addChild("_" + i);
				jarNode.setWarningLevel(FabricTreeWarningLevel.values()[i & 3]);

				if (isFabric) {
					jarNode.iconType = FabricStatusTree.ICON_TYPE_FABRIC_JAR_FILE;
				} else {
					jarNode.iconType = FabricStatusTree.ICON_TYPE_JAR_FILE;
				}

				jarNode.addChild("fabric.mod.json").iconType = FabricStatusTree.ICON_TYPE_JSON;
				jarNode.addChild("fle").iconType = FabricStatusTree.ICON_TYPE_UNKNOWN_FILE;
				jarNode.addChild("mod.class").iconType = FabricStatusTree.ICON_TYPE_JAVA_CLASS;
				jarNode.addChild("net.com.pl.www").iconType = FabricStatusTree.ICON_TYPE_JAVA_PACKAGE;
				jarNode.addChild("assets.lols.whatever").iconType = FabricStatusTree.ICON_TYPE_PACKAGE;
			}
			try {
				open(tree, "--test-fork".equals(args[0]), false);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			System.out.println("Expected 2 arguments: '--from-tree' followed by the tree, or '--test', or '--test-fork'");
			System.exit(-1);
		}
	}

	private static void onForkInternal(String[] args, FabricStatusTree tree) {
		if (tree == null) {
			System.out.println("Status: Invalid tree!");
			System.out.println("Tree Text: " + args[1]);
			System.exit(-1);
		} else {

			// Inform the parent that we have finished reading the tree, so it doesn't need to stop us.
			System.out.println("Status: Correct tree.");

			try {
				openWindow(tree, true);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static Exception m9() {
		return m8();
	}

	private static Exception m8() {
		return m7();
	}

	private static Exception m7() {
		return m6();
	}

	private static Exception m6() {
		return m5();
	}

	private static Exception m5() {
		return new Exception("Test");
	}

	/** @return True if the user has specified the {@link #OPTION_ALWAYS_SHOW_INFO} argument. */
	public static boolean shouldShowInformationGui() {
		// temp for testing
		return true || Boolean.getBoolean(OPTION_ALWAYS_SHOW_INFO);
	}

	/** Opens the given {@link FabricStatusTree} in a new swing window.
	 * 
	 * @param forceFork If true then this will create a new process to host the window, false will always use this
	 *            process, and null will only fork if the current operating system doesn't support LWJGL + swing windows
	 *            at the same time (such as mac osx).
	 * @param shouldWait If true then this call will wait until either the user clicks the "continue" button or the
	 *            window is closed before returning, otherwise this method will return as soon as the window has opened.
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(FabricStatusTree tree, Boolean forceFork, boolean shouldWait) throws Exception {
		final boolean fork;

		if (forceFork != null) {
			fork = forceFork;
		} else {
			fork = shouldFork();
		}

		if (fork) {
			fork(tree, shouldWait);
		} else {
			openWindow(tree, shouldWait);
		}
	}

	private static boolean shouldFork() {
		// if (Boolean.getBoolean(OPTION_ALWAYS_FORK)) {
		if (true) {
			return true;
		}

		String osName = System.getProperty("os.name");

		if (osName.contains(/* Is this the full os name required? */"mac")) {
			return true;
		}

		// TODO: Actually check this on a mac and other operating systems.
		return false;
	}

	private static void fork(FabricStatusTree tree, boolean shouldWait) throws Exception {

		List<String> commands = new ArrayList<>();
		commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		commands.add("-cp");
		commands.add(System.getProperty("java.class.path"));
		commands.add(FabricGuiEntry.class.getName());

		String treeText = tree.write();
		// TODO: Find out from system params if this is a good idea or not?
		if (treeText.length() > 1000) {
			commands.add("--read-tree");
			Path to = Files.createTempFile("fabric-loader-gui-fork", ".json");
			commands.add(to.toAbsolutePath().toString());

			try (FileOutputStream fos = new FileOutputStream(to.toFile())) {
				try (OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
					writer.write(treeText);
				}
			}
		} else {
			commands.add("--from-tree");
			commands.add(treeText);
		}
		treeText = null;

		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.redirectError(Redirect.INHERIT);
		pb.redirectOutput(Redirect.INHERIT);

		try {
			Process p = pb.start();

			// TODO: Handle input (like "Status: Correct tree", or "Status: Continue")

			if (!shouldWait) {
				return;
			}

			try {
				int result = p.waitFor();
				if (result != 0) {
					throw new Exception("Failed to open the gui! (The error should be higher up in the log/output)");
				}
			} catch (InterruptedException e) {

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			//
		}
	}

	private static void openWindow(FabricStatusTree tree, boolean shouldWait) throws Exception {
		FabricMainWindow.open(tree, shouldWait);
	}
}
