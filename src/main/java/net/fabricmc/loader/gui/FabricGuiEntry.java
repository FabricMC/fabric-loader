package net.fabricmc.loader.gui;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.gui.StatusTree.Node;

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
            StatusTree tree = StatusTree.read(args[1]);

            // Perform basic validation
            if (tree == null || tree.fileSystemBasedNode.children.isEmpty()) {
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
            open(m9());
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

    /** @param loadingException if null (and if {@link #OPTION_FORCE_WINDOW} is not present) then this method will exit
     *            immediately. */
    public static void open(Exception loadingException) {
        if (loadingException == null && !Boolean.getBoolean(OPTION_FORCE_WINDOW)) {
            return;
        }
        StatusTree tree = new StatusTree();
        if (loadingException == null) {

        } else {
            Node exception = tree.fileSystemBasedNode.addChild("Crash");
            exception.setError();
            exception.expandByDefault = true;

            StringWriter sw = new StringWriter();
            loadingException.printStackTrace(new PrintWriter(sw));
            exception.details = sw.toString();

            tree.mainErrorText = "Failed to launch!";
        }

        loadingException = null;// force fork
        if (loadingException == null && shouldFork()) {
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

    private static void fork(StatusTree tree) {

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

    private static void openWindow(StatusTree tree) {
        FabricMainWindow.open(tree);
    }
}
