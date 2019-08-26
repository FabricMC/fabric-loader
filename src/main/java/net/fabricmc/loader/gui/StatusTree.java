package net.fabricmc.loader.gui;

import java.util.ArrayList;
import java.util.List;

final class StatusTree {

    enum WarningLevel {
        ERROR,
        WARNING,
        INFO,
        NONE;

        public boolean isWorseThan(WarningLevel other) {
            return ordinal() < other.ordinal();
        }

        public boolean isAtLeast(WarningLevel other) {
            return ordinal() <= other.ordinal();
        }

        public static WarningLevel getWorst(WarningLevel a, WarningLevel b) {
            return a.isWorseThan(b) ? a : b;
        }
    }

    /** No icon is displayed. */
    public static final String ICON_TYPE_DEFAULT = "";

    /** Generic folder */
    public static final String ICON_TYPE_FOLDER = "builtin/folder";

    /** Generic (unknown contents) file */
    public static final String ICON_TYPE_UNKNOWN_FILE = "builtin/file";

    /** Generic non-fabric jar file. */
    public static final String ICON_TYPE_JAR_FILE = "builtin/jar";

    /** Something related to fabric. (It's not defined what exactly this is for, but it uses the main fabric logo). */
    public static final String ICON_TYPE_FABRIC = "builtin/fabric";

    /** Generic json file */
    public static final String ICON_TYPE_JSON = "builtin/json";

    /** A file called "fabric.mod.json". */
    public static final String ICON_TYPE_FABRIC_MOD_JSON = "builtin/json/fabric";

    /** Nodes, organised by how they are accessed from the file system. If there are any errors then this node list will
     * be used instead of {@link #modBasedNode}. */
    public final Node fileSystemBasedNode = new Node(null, "File System");

    /** Nodes organised by the main mod list. */
    public final Node modBasedNode = new Node(null, "Mods");

    /** The text for the error tab. (Unlike the other tabs the error tab displays this text above the node tree). */
    public String mainErrorText = null;

    final class Node {

        private final Node parent;

        public String name;
        public String iconType = ICON_TYPE_DEFAULT;
        private WarningLevel warningLevel = WarningLevel.NONE;

        public boolean expandByDefault = false;

        public final List<Node> children = new ArrayList<>();

        /** Extra text for more information. Lines should be separated by "\n". */
        public String details;

        private Node(Node parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        public WarningLevel getMaximumWarningLevel() {
            return warningLevel;
        }

        public void setWarningLevel(WarningLevel level) {
            if (this.warningLevel == level) {
                return;
            }
            if (warningLevel.isWorseThan(level)) {
                // Just because I haven't written the back-fill revalidation for this
                throw new Error("Why would you set the warning level multiple times?");
            } else {
                if (parent != null && level.isWorseThan(parent.warningLevel)) {
                    parent.setWarningLevel(level);
                }
                this.warningLevel = level;
            }
        }

        public void setError() {
            setWarningLevel(WarningLevel.ERROR);
        }

        public void setWarning() {
            setWarningLevel(WarningLevel.WARNING);
        }

        public void setInfo() {
            setWarningLevel(WarningLevel.INFO);
        }

        public Node addChild(String string) {
            Node child = new Node(this, string);
            children.add(child);
            return child;
        }

    }
}
