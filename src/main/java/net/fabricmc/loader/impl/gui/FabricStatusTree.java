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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;

import net.fabricmc.loader.impl.FormattedException;

public final class FabricStatusTree {
	public enum FabricTreeWarningLevel {
		ERROR,
		WARN,
		INFO,
		NONE;

		public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

		public boolean isHigherThan(FabricTreeWarningLevel other) {
			return ordinal() < other.ordinal();
		}

		public boolean isAtLeast(FabricTreeWarningLevel other) {
			return ordinal() <= other.ordinal();
		}

		public static FabricTreeWarningLevel getHighest(FabricTreeWarningLevel a, FabricTreeWarningLevel b) {
			return a.isHigherThan(b) ? a : b;
		}
	}

	public enum FabricBasicButtonType {
		/** Sends the status message to the main application, then disables itself. */
		CLICK_ONCE,
		/** Sends the status message to the main application, remains enabled. */
		CLICK_MANY;
	}

	/** No icon is displayed. */
	public static final String ICON_TYPE_DEFAULT = "";
	/** Generic folder. */
	public static final String ICON_TYPE_FOLDER = "folder";
	/** Generic (unknown contents) file. */
	public static final String ICON_TYPE_UNKNOWN_FILE = "file";
	/** Generic non-Fabric jar file. */
	public static final String ICON_TYPE_JAR_FILE = "jar";
	/** Generic Fabric-related jar file. */
	public static final String ICON_TYPE_FABRIC_JAR_FILE = "jar+fabric";
	/** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_FABRIC = "fabric";
	/** Generic JSON file. */
	public static final String ICON_TYPE_JSON = "json";
	/** A file called "fabric.mod.json". */
	public static final String ICON_TYPE_FABRIC_JSON = "json+fabric";
	/** Java bytecode class file. */
	public static final String ICON_TYPE_JAVA_CLASS = "java_class";
	/** A folder inside of a Java JAR. */
	public static final String ICON_TYPE_PACKAGE = "package";
	/** A folder that contains Java class files. */
	public static final String ICON_TYPE_JAVA_PACKAGE = "java_package";
	/** A tick symbol, used to indicate that something matched. */
	public static final String ICON_TYPE_TICK = "tick";
	/** A cross symbol, used to indicate that something didn't match (although it's not an error). Used as the opposite
	 * of {@link #ICON_TYPE_TICK} */
	public static final String ICON_TYPE_LESSER_CROSS = "lesser_cross";

	public final String title;
	public final String mainText;
	public final List<FabricStatusTab> tabs = new ArrayList<>();
	public final List<FabricStatusButton> buttons = new ArrayList<>();

	public FabricStatusTree(String title, String mainText) {
		Objects.requireNonNull(title, "null title");
		Objects.requireNonNull(mainText, "null mainText");

		this.title = title;
		this.mainText = mainText;
	}

	public FabricStatusTree(DataInputStream is) throws IOException {
		title = is.readUTF();
		mainText = is.readUTF();

		for (int i = is.readInt(); i > 0; i--) {
			tabs.add(new FabricStatusTab(is));
		}

		for (int i = is.readInt(); i > 0; i--) {
			buttons.add(new FabricStatusButton(is));
		}
	}

	public void writeTo(DataOutputStream os) throws IOException {
		os.writeUTF(title);
		os.writeUTF(mainText);
		os.writeInt(tabs.size());

		for (FabricStatusTab tab : tabs) {
			tab.writeTo(os);
		}

		os.writeInt(buttons.size());

		for (FabricStatusButton button : buttons) {
			button.writeTo(os);
		}
	}

	public FabricStatusTab addTab(String name) {
		FabricStatusTab tab = new FabricStatusTab(name);
		tabs.add(tab);
		return tab;
	}

	public FabricStatusButton addButton(String text, FabricBasicButtonType type) {
		FabricStatusButton button = new FabricStatusButton(text, type);
		buttons.add(button);
		return button;
	}

	public static final class FabricStatusButton {
		public final String text;
		public final FabricBasicButtonType type;
		public String clipboard;
		public boolean shouldClose, shouldContinue;

		public FabricStatusButton(String text, FabricBasicButtonType type) {
			Objects.requireNonNull(text, "null text");

			this.text = text;
			this.type = type;
		}

		public FabricStatusButton(DataInputStream is) throws IOException {
			text = is.readUTF();
			type = FabricBasicButtonType.valueOf(is.readUTF());
			shouldClose = is.readBoolean();
			shouldContinue = is.readBoolean();

			if (is.readBoolean()) clipboard = is.readUTF();
		}

		public void writeTo(DataOutputStream os) throws IOException {
			os.writeUTF(text);
			os.writeUTF(type.name());
			os.writeBoolean(shouldClose);
			os.writeBoolean(shouldContinue);

			if (clipboard != null) {
				os.writeBoolean(true);
				os.writeUTF(clipboard);
			} else {
				os.writeBoolean(false);
			}
		}

		public FabricStatusButton makeClose() {
			shouldClose = true;
			return this;
		}

		public FabricStatusButton makeContinue() {
			this.shouldContinue = true;
			return this;
		}

		public FabricStatusButton withClipboard(String clipboard) {
			this.clipboard = clipboard;
			return this;
		}
	}

	public static final class FabricStatusTab {
		public final FabricStatusNode node;

		/** The minimum warning level to display for this tab. */
		public FabricTreeWarningLevel filterLevel = FabricTreeWarningLevel.NONE;

		public FabricStatusTab(String name) {
			this.node = new FabricStatusNode(null, name);
		}

		public FabricStatusTab(DataInputStream is) throws IOException {
			node = new FabricStatusNode(null, is);
			filterLevel = FabricTreeWarningLevel.valueOf(is.readUTF());
		}

		public void writeTo(DataOutputStream os) throws IOException {
			node.writeTo(os);
			os.writeUTF(filterLevel.name());
		}

		public FabricStatusNode addChild(String name) {
			return node.addChild(name);
		}
	}

	public static final class FabricStatusNode {
		private FabricStatusNode parent;
		public String name;
		/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
		 * {@link #setWarningLevel(FabricTreeWarningLevel) warning level} is set to
		 * {@link FabricTreeWarningLevel#NONE } */
		public String iconType = ICON_TYPE_DEFAULT;
		private FabricTreeWarningLevel warningLevel = FabricTreeWarningLevel.NONE;
		public boolean expandByDefault = false;
		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;
		public final List<FabricStatusNode> children = new ArrayList<>();

		private FabricStatusNode(FabricStatusNode parent, String name) {
			Objects.requireNonNull(name, "null name");

			this.parent = parent;
			this.name = name;
		}

		public FabricStatusNode(FabricStatusNode parent, DataInputStream is) throws IOException {
			this.parent = parent;

			name = is.readUTF();
			iconType = is.readUTF();
			warningLevel = FabricTreeWarningLevel.valueOf(is.readUTF());
			expandByDefault = is.readBoolean();
			if (is.readBoolean()) details = is.readUTF();

			for (int i = is.readInt(); i > 0; i--) {
				children.add(new FabricStatusNode(this, is));
			}
		}

		public void writeTo(DataOutputStream os) throws IOException {
			os.writeUTF(name);
			os.writeUTF(iconType);
			os.writeUTF(warningLevel.name());
			os.writeBoolean(expandByDefault);
			os.writeBoolean(details != null);
			if (details != null) os.writeUTF(details);
			os.writeInt(children.size());

			for (FabricStatusNode child : children) {
				child.writeTo(os);
			}
		}

		public void moveTo(FabricStatusNode newParent) {
			parent.children.remove(this);
			this.parent = newParent;
			newParent.children.add(this);
		}

		public FabricTreeWarningLevel getMaximumWarningLevel() {
			return warningLevel;
		}

		public void setWarningLevel(FabricTreeWarningLevel level) {
			if (this.warningLevel == level) {
				return;
			}

			if (warningLevel.isHigherThan(level)) {
				// Just because I haven't written the back-fill revalidation for this
				throw new Error("Why would you set the warning level multiple times?");
			} else {
				if (parent != null && level.isHigherThan(parent.warningLevel)) {
					parent.setWarningLevel(level);
				}

				this.warningLevel = level;
				expandByDefault |= level.isAtLeast(FabricTreeWarningLevel.WARN);
			}
		}

		public void setError() {
			setWarningLevel(FabricTreeWarningLevel.ERROR);
		}

		public void setWarning() {
			setWarningLevel(FabricTreeWarningLevel.WARN);
		}

		public void setInfo() {
			setWarningLevel(FabricTreeWarningLevel.INFO);
		}

		private FabricStatusNode addChild(String string) {
			if (string.startsWith("\t")) {
				if (children.size() == 0) {
					FabricStatusNode rootChild = new FabricStatusNode(this, "");
					children.add(rootChild);
				}

				FabricStatusNode lastChild = children.get(children.size() - 1);
				lastChild.addChild(string.substring(1));
				lastChild.expandByDefault = true;
				return lastChild;
			} else {
				FabricStatusNode child = new FabricStatusNode(this, cleanForNode(string));
				children.add(child);
				return child;
			}
		}

		private String cleanForNode(String string) {
			string = string.trim();

			if (string.length() > 1) {
				if (string.startsWith("-")) {
					string = string.substring(1);
					string = string.trim();
				}
			}

			return string;
		}

		public FabricStatusNode addMessage(String message, FabricTreeWarningLevel warningLevel) {
			String[] lines = message.split("\n");

			FabricStatusNode sub = new FabricStatusNode(this, lines[0]);
			children.add(sub);
			sub.setWarningLevel(warningLevel);

			for (int i = 1; i < lines.length; i++) {
				sub.addChild(lines[i]);
			}

			return sub;
		}

		public FabricStatusNode addException(Throwable exception) {
			return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, UnaryOperator.identity(), new StackTraceElement[0]);
		}

		public FabricStatusNode addCleanedException(Throwable exception) {
			return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, e -> {
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

				return e;
			}, new StackTraceElement[0]);
		}

		private static FabricStatusNode addException(FabricStatusNode node, Set<Throwable> seen, Throwable exception, UnaryOperator<Throwable> filter, StackTraceElement[] parentTrace) {
			if (!seen.add(exception)) {
				return node;
			}

			exception = filter.apply(exception);
			FabricStatusNode sub = node.addException(exception, parentTrace);
			StackTraceElement[] trace = exception.getStackTrace();

			for (Throwable t : exception.getSuppressed()) {
				FabricStatusNode suppressed = addException(sub, seen, t, filter, trace);
				suppressed.name += " (suppressed)";
				suppressed.expandByDefault = false;
			}

			if (exception.getCause() != null) {
				addException(sub, seen, exception.getCause(), filter, trace);
			}

			return sub;
		}

		private FabricStatusNode addException(Throwable exception, StackTraceElement[] parentTrace) {
			boolean showTrace = !(exception instanceof FormattedException) || exception.getCause() != null;
			String msg;

			if (exception instanceof FormattedException) {
				msg = Objects.toString(exception.getMessage());
			} else if (exception.getMessage() == null || exception.getMessage().isEmpty()) {
				msg = exception.toString();
			} else {
				msg = String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage());
			}

			FabricStatusNode sub = addMessage(msg, FabricTreeWarningLevel.ERROR);

			if (!showTrace) return sub;

			StackTraceElement[] trace = exception.getStackTrace();
			int uniqueFrames = trace.length - 1;

			for (int i = parentTrace.length - 1; uniqueFrames >= 0 && i >= 0 && trace[uniqueFrames].equals(parentTrace[i]); i--) {
				uniqueFrames--;
			}

			StringJoiner frames = new StringJoiner("\n");
			int inheritedFrames = trace.length - 1 - uniqueFrames;

			for (int i = 0; i <= uniqueFrames; i++) {
				frames.add("at " + trace[i]);
			}

			if (inheritedFrames > 0) {
				frames.add("... " + inheritedFrames + " more");
			}

			sub.addChild(frames.toString()).iconType = ICON_TYPE_JAVA_CLASS;

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
