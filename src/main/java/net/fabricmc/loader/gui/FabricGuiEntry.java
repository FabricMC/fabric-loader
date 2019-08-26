package net.fabricmc.loader.gui;

import java.io.PrintWriter;
import java.io.StringWriter;

import net.fabricmc.loader.gui.StatusTree.Node;

/** The main entry point for all fabric-based stuff. */
public final class FabricGuiEntry {

    /**
     * 
     */
    public static final String OPTION_FORCE_WINDOW = "fabric.always_show_info";

    /** The entry point for forking the main application over into a different process to get around incompatibilities
     * on OSX, and to separate the main launch from the swing runtime. (This is only used if no errors are present, but
     * the user has specified the {@link #OPTION_FORCE_WINDOW} flag. */
    public static void main(String[] args) {
        // TODO: read from standard input to determine what to display
        // displayWindow(new String[] { "Separate launching not added yet!" });
        // throw new AbstractMethodError("Separate launching not added yet!");

        openWindow(m9());
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
        return m4();
    }

    private static Exception m4() {
        return m3();
    }

    private static Exception m3() {
        return m2();
    }

    private static Exception m2() {
        return new Exception("Test");
    }

    /** @param loadingException if null (and if {@link #OPTION_FORCE_WINDOW} is not present) then this method will exit
     *            immediately. */
    public static void openWindow(Exception loadingException) {
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

        for (int i = 0; i < 100; i++) {
            tree.fileSystemBasedNode.addChild("int " + i);
        }

        displayWindow(tree);
    }

    private static void displayWindow(StatusTree tree) {
        FabricMainWindow.open(tree);
    }
}
