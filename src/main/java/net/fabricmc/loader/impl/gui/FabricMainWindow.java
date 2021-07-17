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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusButton;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricTreeWarningLevel;

class FabricMainWindow {
	static Icon missingIcon = null;

	static void open(FabricStatusTree tree, boolean shouldWait) throws Exception {
		if (GraphicsEnvironment.isHeadless()) {
			throw new HeadlessException();
		}

		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		open0(tree, shouldWait);
	}

	private static void open0(FabricStatusTree tree, boolean shouldWait) throws Exception {
		CountDownLatch guiTerminatedLatch = new CountDownLatch(1);

		SwingUtilities.invokeAndWait(() -> {
			createUi(guiTerminatedLatch, tree);
		});

		if (shouldWait) {
			guiTerminatedLatch.await();
		}
	}

	private static void createUi(CountDownLatch onCloseLatch, FabricStatusTree tree) {
		JFrame window = new JFrame();
		window.setVisible(false);

		String version = getTitleVersion();

		if (version == null) {
			window.setTitle("Fabric Loader");
		} else {
			window.setTitle("Fabric Loader " + version);
		}

		try {
			window.setIconImage(loadImage("/ui/icon/fabric_x128.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		window.setMinimumSize(new Dimension(640, 480));
		window.setLocationByPlatform(true);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				onCloseLatch.countDown();
			}
		});

		Container contentPane = window.getContentPane();

		if (tree.mainText != null && !tree.mainText.isEmpty()) {
			JLabel errorLabel = new JLabel(tree.mainText);
			errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
			Font font = errorLabel.getFont();
			errorLabel.setFont(font.deriveFont(font.getSize() * 2.0f));
			contentPane.add(errorLabel, BorderLayout.NORTH);
		}

		IconSet icons = new IconSet();

		if (tree.tabs.isEmpty()) {
			FabricStatusTab tab = new FabricStatusTab("Opening Errors");
			tab.addChild("No tabs provided! (Something is very broken)").setError();
			contentPane.add(createTreePanel(tab.node, tab.filterLevel, icons), BorderLayout.CENTER);
		} else if (tree.tabs.size() == 1) {
			FabricStatusTab tab = tree.tabs.get(0);
			contentPane.add(createTreePanel(tab.node, tab.filterLevel, icons), BorderLayout.CENTER);
		} else {
			JTabbedPane tabs = new JTabbedPane();
			contentPane.add(tabs, BorderLayout.CENTER);

			for (FabricStatusTab tab : tree.tabs) {
				tabs.addTab(tab.node.name, createTreePanel(tab.node, tab.filterLevel, icons));
			}
		}

		if (!tree.buttons.isEmpty()) {
			JPanel buttons = new JPanel();
			contentPane.add(buttons, BorderLayout.SOUTH);
			buttons.setLayout(new FlowLayout(FlowLayout.TRAILING));

			for (FabricStatusButton button : tree.buttons) {
				JButton btn = new JButton(button.text);
				buttons.add(btn);
				btn.addActionListener(e -> {
					btn.setEnabled(false);

					if (button.shouldClose) {
						window.dispose();
					}

					if (button.shouldContinue) {
						onCloseLatch.countDown();
					}
				});
			}
		}

		window.setVisible(true);
		window.requestFocus();
	}

	private static String getTitleVersion() {
		Optional<ModContainer> optional = FabricLoader.getInstance().getModContainer("fabricloader");

		if (optional.isPresent()) {
			return optional
				.get()
				.getMetadata()
				.getVersion()
				.getFriendlyString();
		}

		return null;
	}

	private static JPanel createTreePanel(FabricStatusNode rootNode, FabricTreeWarningLevel minimumWarningLevel,
			IconSet iconSet) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		TreeNode treeNode = new CustomTreeNode(null, rootNode, minimumWarningLevel);

		DefaultTreeModel model = new DefaultTreeModel(treeNode);
		JTree tree = new JTree(model);
		tree.setRootVisible(false);

		for (int row = 0; row < tree.getRowCount(); row++) {
			if (!tree.isVisible(tree.getPathForRow(row))) {
				continue;
			}

			CustomTreeNode node = ((CustomTreeNode) tree.getPathForRow(row).getLastPathComponent());

			if (node.node.expandByDefault || node.node.getMaximumWarningLevel().isAtLeast(FabricTreeWarningLevel.WARN)) {
				tree.expandRow(row);
			}
		}

		ToolTipManager.sharedInstance().registerComponent(tree);
		tree.setCellRenderer(new CustomTreeCellRenderer(iconSet));

		JScrollPane scrollPane = new JScrollPane(tree);
		panel.add(scrollPane);

		return panel;
	}

	private static BufferedImage loadImage(String str) throws IOException {
		return ImageIO.read(loadStream(str));
	}

	private static InputStream loadStream(String str) throws FileNotFoundException {
		InputStream stream = FabricMainWindow.class.getResourceAsStream(str);

		if (stream == null) {
			throw new FileNotFoundException(str);
		}

		return stream;
	}

	static final class IconSet {
		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<IconInfo, Map<Integer, Icon>> icons = new HashMap<>();

		public Icon get(IconInfo info) {
			// TODO: HDPI

			int scale = 16;
			Map<Integer, Icon> map = icons.get(info);

			if (map == null) {
				icons.put(info, map = new HashMap<>());
			}

			Icon icon = map.get(scale);

			if (icon == null) {
				try {
					icon = loadIcon(info, scale);
				} catch (IOException e) {
					e.printStackTrace();
					icon = missingIcon();
				}

				map.put(scale, icon);
			}

			return icon;
		}
	}

	private static Icon missingIcon() {
		if (missingIcon == null) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);

			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					img.setRGB(x, y, 0xff_ff_f2);
				}
			}

			for (int i = 0; i < 16; i++) {
				img.setRGB(0, i, 0x22_22_22);
				img.setRGB(15, i, 0x22_22_22);
				img.setRGB(i, 0, 0x22_22_22);
				img.setRGB(i, 15, 0x22_22_22);
			}

			for (int i = 3; i < 13; i++) {
				img.setRGB(i, i, 0x9b_00_00);
				img.setRGB(i, 16 - i, 0x9b_00_00);
			}

			missingIcon = new ImageIcon(img);
		}

		return missingIcon;
	}

	private static Icon loadIcon(IconInfo info, int scale) throws IOException {
		BufferedImage img = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D imgG2d = img.createGraphics();

		BufferedImage main = loadImage("/ui/icon/" + info.mainPath + "_x" + scale + ".png");
		assert main.getWidth() == scale;
		assert main.getHeight() == scale;
		imgG2d.drawImage(main, null, 0, 0);

		final int[][] coords = { { 0, 8 }, { 8, 8 }, { 8, 0 } };

		for (int i = 0; i < info.decor.length; i++) {
			String decor = info.decor[i];

			if (decor == null) {
				continue;
			}

			BufferedImage decorImg = loadImage("/ui/icon/decoration/" + decor + "_x" + (scale / 2) + ".png");
			assert decorImg.getWidth() == scale / 2;
			assert decorImg.getHeight() == scale / 2;
			imgG2d.drawImage(decorImg, null, coords[i][0], coords[i][1]);
		}

		return new ImageIcon(img);
	}

	static final class IconInfo {
		public final String mainPath;
		public final String[] decor;
		private final int hash;

		IconInfo(String mainPath) {
			this.mainPath = mainPath;
			this.decor = new String[0];
			hash = mainPath.hashCode();
		}

		IconInfo(String mainPath, String[] decor) {
			this.mainPath = mainPath;
			this.decor = decor;
			assert decor.length < 4 : "Cannot fit more than 3 decorations into an image (and leave space for the background)";

			if (decor.length == 0) {
				// To mirror the no-decor constructor
				hash = mainPath.hashCode();
			} else {
				hash = mainPath.hashCode() * 31 + Arrays.hashCode(decor);
			}
		}

		public static IconInfo fromNode(FabricStatusNode node) {
			String[] split = node.iconType.split("\\+");

			if (split.length == 1 && split[0].isEmpty()) {
				split = new String[0];
			}

			final String main;
			List<String> decors = new ArrayList<>();
			FabricTreeWarningLevel warnLevel = node.getMaximumWarningLevel();

			if (split.length == 0) {
				// Empty string, but we might replace it with a warning
				if (warnLevel == FabricTreeWarningLevel.NONE) {
					main = "missing";
				} else {
					main = "level_" + warnLevel.lowerCaseName;
				}
			} else {
				main = split[0];

				if (warnLevel == FabricTreeWarningLevel.NONE) {
					// Just to add a gap
					decors.add(null);
				} else {
					decors.add("level_" + warnLevel.lowerCaseName);
				}

				for (int i = 1; i < split.length && i < 3; i++) {
					decors.add(split[i]);
				}
			}

			return new IconInfo(main, decors.toArray(new String[0]));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}

			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}

			IconInfo other = (IconInfo) obj;
			return mainPath.equals(other.mainPath) && Arrays.equals(decor, other.decor);
		}
	}

	private static final class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -5621219150752332739L;

		private final IconSet iconSet;

		private CustomTreeCellRenderer(IconSet icons) {
			this.iconSet = icons;
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			if (value instanceof CustomTreeNode) {
				CustomTreeNode c = (CustomTreeNode) value;
				setIcon(iconSet.get(c.getIconInfo()));

				if (c.node.details == null || c.node.details.isEmpty()) {
					setToolTipText(null);
				} else {
					if (c.node.details.contains("\n")) {
						// It's a bit odd but it's easier than creating a custom tooltip
						String replaced = c.node.details//
								.replace("&", "&amp;")//
								.replace("<", "&lt;")//
								.replace(">", "&gt;")//
								.replace("\n", "<br>");
						setToolTipText("<html>" + replaced + "</html>");
					} else {
						setToolTipText(c.node.details);
					}
				}
			}

			return this;
		}
	}

	static class CustomTreeNode implements TreeNode {
		public final TreeNode parent;
		public final FabricStatusNode node;
		public final List<CustomTreeNode> displayedChildren = new ArrayList<>();
		private IconInfo iconInfo;

		CustomTreeNode(TreeNode parent, FabricStatusNode node, FabricTreeWarningLevel minimumWarningLevel) {
			this.parent = parent;
			this.node = node;

			for (FabricStatusNode c : node.children) {
				if (minimumWarningLevel.isHigherThan(c.getMaximumWarningLevel())) {
					continue;
				}

				displayedChildren.add(new CustomTreeNode(this, c, minimumWarningLevel));
			}
		}

		public IconInfo getIconInfo() {
			if (iconInfo == null) {
				iconInfo = IconInfo.fromNode(node);
			}

			return iconInfo;
		}

		@Override
		public String toString() {
			return node.name;
		}

		@Override
		public TreeNode getChildAt(int childIndex) {
			return displayedChildren.get(childIndex);
		}

		@Override
		public int getChildCount() {
			return displayedChildren.size();
		}

		@Override
		public TreeNode getParent() {
			return parent;
		}

		@Override
		public int getIndex(TreeNode node) {
			return displayedChildren.indexOf(node);
		}

		@Override
		public boolean getAllowsChildren() {
			return !isLeaf();
		}

		@Override
		public boolean isLeaf() {
			return displayedChildren.isEmpty();
		}

		@Override
		public Enumeration<CustomTreeNode> children() {
			return new Enumeration<CustomTreeNode>() {
				Iterator<CustomTreeNode> it = displayedChildren.iterator();

				@Override
				public boolean hasMoreElements() {
					return it.hasNext();
				}

				@Override
				public CustomTreeNode nextElement() {
					return it.next();
				}
			};
		}
	}
}
