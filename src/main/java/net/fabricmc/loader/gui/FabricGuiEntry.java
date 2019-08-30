package net.fabricmc.loader.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.gui.FabricStatusTree.WarningLevel;

/** The main entry point for all fabric-based stuff. */
public final class FabricGuiEntry {

	public static final String OPTION_ALWAYS_SHOW_INFO = "fabric_loader.info_gui.always_show";
	public static final String OPTION_DISABLE_WAIT = "fabric_loader.info_gui.disable_wait";
	public static final String OPTION_ALWAYS_FORK = "fabric_loader.info_gui.always_fork";

	/** The entry point for forking the main application over into a different process to get around incompatibilities
	 * on OSX, and to separate the main launch from the swing runtime. (This is only used if no errors are present, but
	 * the user has specified the {@link #OPTION_ALWAYS_SHOW_INFO} flag. */
	public static void main(String[] args) {

		if (args.length == 2 && "--from-tree".equals(args[0])) {
		    // Forked opening
		    FabricStatusTree tree = FabricStatusTree.read(args[1]);

		    // Perform basic validation
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
		    return;
		} else if (args.length == 1 && "--test".equals(args[0])) {
		    // Test code
		    FabricStatusTree tree = new FabricStatusTree();
		    tree.mainErrorText = "Failed to launch!";

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
		        jarNode.setWarningLevel(WarningLevel.values()[i & 3]);
		        if (isFabric) {
		            jarNode.iconType = FabricStatusTree.ICON_TYPE_JAR_FILE + "+fabric";
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
		        open(false, tree);
		    } catch (Exception e) {
		        throw new RuntimeException(e);
		    }
		} else {
		    System.out.println("Expected 2 arguments: '--from-tree' followed by the tree, or '--test'");
		    System.exit(-1);
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

	/** @throws Exception if something went wrong while opening the window. */
	public static void open(boolean isCrashing, FabricStatusTree tree) throws Exception {
		open(isCrashing, tree, !Boolean.getBoolean(OPTION_DISABLE_WAIT));
	}

	/** @throws Exception if something went wrong while opening the window. */
	public static void open(boolean isCrashing, FabricStatusTree tree, boolean shouldWait) throws Exception {
		if (!isCrashing && shouldFork()) {
		    fork(tree, shouldWait);
		} else {
		    openWindow(tree, shouldWait);
		}
	}

	private static boolean shouldFork() {
		if (Boolean.getBoolean(OPTION_ALWAYS_FORK)) {
		    return true;
		}
		String osName = System.getProperty("os.name");
		if (osName.contains(/* Is this the full os name required? */"mac")) {
		    return true;
		}
		// TODO: Actually check this on a mac and other operating systems.
		return false;
	}

	private static void fork(FabricStatusTree tree, boolean shouldWait) {

		List<String> commands = new ArrayList<>();
		commands.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		commands.add("-cp");
		commands.add(System.getProperty("java.class.path"));
		commands.add(FabricGuiEntry.class.getName());
		commands.add("--from-tree");
		commands.add(tree.write());
		ProcessBuilder pb = new ProcessBuilder(commands);

		pb.inheritIO();

		try {
		    Process p = pb.start();
		    // Always halt until it closes
		    boolean hasStartedUp = false;

		    if (!shouldWait) {
		        return;
		    }
		    try {
		        p.waitFor();
		    } catch (InterruptedException e) {
		        p.destroy();
		    }

		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}
	}

	private static void openWindow(FabricStatusTree tree, boolean shouldWait) throws Exception {
		FabricMainWindow.open(tree, shouldWait);
	}
}
