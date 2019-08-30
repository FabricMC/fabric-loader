package net.fabricmc.loader.gui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FabricStatusTree {

	enum WarningLevel {
		ERROR,
		WARN,
		INFO,
		NONE;

		public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

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
	public static final String ICON_TYPE_FOLDER = "folder";

	/** Generic (unknown contents) file */
	public static final String ICON_TYPE_UNKNOWN_FILE = "file";

	/** Generic non-fabric jar file. */
	public static final String ICON_TYPE_JAR_FILE = "jar";

	/** Generic fabric-related jar file. */
	public static final String ICON_TYPE_FABRIC_JAR_FILE = "jar+fabric";

	/** Something related to fabric. (It's not defined what exactly this is for, but it uses the main fabric logo). */
	public static final String ICON_TYPE_FABRIC = "fabric";

	/** Generic json file */
	public static final String ICON_TYPE_JSON = "json";

	/** A file called "fabric.mod.json". */
	public static final String ICON_TYPE_FABRIC_JSON = "json+fabric";

	/** Java bytecode class file. */
	public static final String ICON_TYPE_JAVA_CLASS = "java_class";

	/** A folder inside of a java jar. */
	public static final String ICON_TYPE_PACKAGE = "package";

	/** A folder that contains java class files. */
	public static final String ICON_TYPE_JAVA_PACKAGE = "java_package";

	/** A tick symbol, used to indicate that something matched. */
	public static final String ICON_TYPE_TICK = "tick";

	/** A cross symbol, used to indicate that something didn't match. (Although it's not an error). Used as the opposite
	 * of {@link #ICON_TYPE_TICK} */
	public static final String ICON_TYPE_LESSER_CROSS = "lesser_cross";

	/** Every node present in this list. */
	public final List<FabricStatusTab> tabs = new ArrayList<>();

	/** The text for the error tab. (Unlike the other tabs the error tab displays this text above the node tree). */
	public String mainErrorText = null;

	public static FabricStatusTree read(String from) {
		FabricStatusTree tree = new FabricStatusTree();

		return tree;
	}

	public String write() {
		StringBuilder sb = new StringBuilder();

		return sb.toString();
	}

	public FabricStatusTab addTab(String name) {
		FabricStatusTab tab = new FabricStatusTab(name);
		tabs.add(tab);
		return tab;
	}

	public static final class FabricStatusTab {
		public final FabricStatusNode node;

		/** The minimum warning level to display for this tab. */
		public WarningLevel filterLevel = WarningLevel.NONE;

		public FabricStatusTab(String name) {
			this.node = new FabricStatusNode(null, name);
		}

		public FabricStatusNode addChild(String name) {
			return node.addChild(name);
		}
	}

	public static final class FabricStatusNode {

		private FabricStatusNode parent;

		public String name;

		/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
		 * {@link #setWarningLevel(WarningLevel) warning level} is set to {@link WarningLevel#NONE } */
		public String iconType = ICON_TYPE_DEFAULT;

		private WarningLevel warningLevel = WarningLevel.NONE;

		public boolean expandByDefault = false;

		public final List<FabricStatusNode> children = new ArrayList<>();

		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;

		private FabricStatusNode(FabricStatusNode parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		public void moveTo(FabricStatusNode newParent) {
			parent.children.remove(this);
			this.parent = newParent;
			newParent.children.add(this);
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
			setWarningLevel(WarningLevel.WARN);
		}

		public void setInfo() {
			setWarningLevel(WarningLevel.INFO);
		}

		public FabricStatusNode addChild(String string) {
			FabricStatusNode child = new FabricStatusNode(this, string);
			children.add(child);
			return child;
		}

		/** Calls {@link #addException(Throwable)}, and throws the given exception */
		public <T extends Throwable> T addAndThrow(T exception) throws T {
			addException(exception);
			throw exception;
		}

		public FabricStatusNode addException(Throwable exception) {
			FabricStatusNode sub = new FabricStatusNode(this, "...");
			children.add(sub);

			sub.setError();
			String msg = exception.getMessage();
			String[] lines = msg.split("\n");
			if (lines.length == 0) {
				// what
				lines = new String[] { msg };
			}
			sub.name = lines[0];
			for (int i = 1; i < lines.length; i++) {
				sub.addChild(lines[i]);
			}

			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			sub.details = sw.toString();

			return sub;
		}

		/** If this node has one child then it merges the child node into this one. */
		public void mergeWithSingleChild(String join) {
			if (children.size() != 1) {
				return;
			}
			FabricStatusNode child = children.remove(0);
			name += join + child.name;
			for (FabricStatusNode cc : child.children) {
				cc.parent = this;
				children.add(cc);
			}
			child.children.clear();
		}

		public void mergeSingleChildFilePath(String folderType) {
			if (!iconType.equals(folderType)) {
				return;
			}
			while (children.size() == 1 && children.get(0).iconType.equals(folderType)) {
				mergeWithSingleChild("/");
			}
			children.sort((a, b) -> a.name.compareTo(b.name));
			mergeChildFilePaths(folderType);
		}

		public void mergeChildFilePaths(String folderType) {
			for (FabricStatusNode node : children) {
				node.mergeSingleChildFilePath(folderType);
			}
		}

		public FabricStatusNode getFileNode(String file, String folderType, String fileType) {
			FabricStatusNode fileNode = this;
			pathIteration: for (String s : file.split("/")) {
				if (s.isEmpty()) {
					continue;
				}
				for (FabricStatusNode c : fileNode.children) {
					if (c.name.equals(s)) {
					    fileNode = c;
					    continue pathIteration;
					}
				}
				if (fileNode.iconType.equals(FabricStatusTree.ICON_TYPE_DEFAULT)) {
					fileNode.iconType = folderType;
				}
				fileNode = fileNode.addChild(s);
			}
			fileNode.iconType = fileType;
			return fileNode;
		}
	}
}
