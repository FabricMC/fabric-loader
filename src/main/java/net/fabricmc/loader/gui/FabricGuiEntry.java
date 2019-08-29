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

    /**
     * 
     */
    public static final String OPTION_FORCE_WINDOW = "fabric_loader.always_show_info";

    /** The entry point for forking the main application over into a different process to get around incompatibilities
     * on OSX, and to separate the main launch from the swing runtime. (This is only used if no errors are present, but
     * the user has specified the {@link #OPTION_FORCE_WINDOW} flag. */
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
                openWindow(tree);
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

            open(false, tree);
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

    /** @return True if the user has specified the {@link #OPTION_FORCE_WINDOW} argument. */
    public static boolean shouldShowInformationGui() {
        return Boolean.getBoolean(OPTION_FORCE_WINDOW);
    }

    public static void open(boolean isCrashing, FabricStatusTree tree) {
        if (!isCrashing && shouldFork()) {
            fork(tree);
        } else {
            openWindow(tree);
        }
    }

    private static boolean shouldFork() {
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        System.out.println(osName);
        System.out.println(osArch);

        return true;
    }

    private static void fork(FabricStatusTree tree) {

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

    private static void openWindow(FabricStatusTree tree) {
        FabricMainWindow.open(tree);
    }
}
