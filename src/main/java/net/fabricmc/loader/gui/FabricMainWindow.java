package net.fabricmc.loader.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import net.fabricmc.loader.gui.StatusTree.Node;
import net.fabricmc.loader.gui.StatusTree.WarningLevel;

class FabricMainWindow {

    static void open(StatusTree tree) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            open0(tree);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void open0(StatusTree tree) throws Exception {
        CountDownLatch guiTerminatedLatch = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            createUi(guiTerminatedLatch, tree);
        });
        guiTerminatedLatch.await();
    }

    private static void createUi(CountDownLatch onCloseLatch, StatusTree tree) {
        JFrame window = new JFrame();
        window.setVisible(false);
        window.setTitle("Fabric Loader");
        window.setMinimumSize(new Dimension(640, 480));
        window.setLocationByPlatform(true);
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                onCloseLatch.countDown();
            }
        });
        // window.setIconImage(BufferedImage.);

        Container contentPane = window.getContentPane();

        JTabbedPane tabs = new JTabbedPane();
        contentPane.add(tabs, BorderLayout.CENTER);

        if (tree.fileSystemBasedNode.getMaximumWarningLevel() == WarningLevel.ERROR) {
            tabs.addTab("Errors", createErrorPanel(tree.mainErrorText, tree.fileSystemBasedNode));
        }
        tabs.addTab(tree.fileSystemBasedNode.name, createTreePanel(tree.fileSystemBasedNode));
        tabs.addTab(tree.modBasedNode.name, createTreePanel(tree.modBasedNode));

        window.setVisible(true);
    }

    private static JPanel createErrorPanel(String errorText, Node rootNode) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JTextArea text = new JTextArea(errorText);
        text.setEditable(false);
        panel.add(text, BorderLayout.NORTH);

        panel.add(createTreePanel(rootNode, WarningLevel.ERROR), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel createTreePanel(Node rootNode) {
        return createTreePanel(rootNode, WarningLevel.NONE);
    }

    private static JPanel createTreePanel(Node rootNode, WarningLevel minimumWarningLevel) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        TreeNode treeNode = new CustomTreeNode(null, rootNode, minimumWarningLevel);

        DefaultTreeModel model = new DefaultTreeModel(treeNode);
        JTree tree = new JTree(model);
        tree.setRootVisible(false);

        int[] colours = { 0xFF_AA_22_22, 0xFF_99_99_22, 0xFF_00_66_BB, 0xFF_00_55_00 };
        String[] desc = { "Error", "Warning", "Info", "None" };
        Icon[] icons = new Icon[4];

        for (int i = 0; i < 4; i++) {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 9; y < 15; y++) {
                for (int x = 9; x < 15; x++) {
                    img.setRGB(x, y, colours[i]);
                }
            }
            icons[i] = new ImageIcon(img, desc[i]);
        }

        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setCellRenderer(new CustomTreeCellRenderer(icons));

        JScrollPane treeView = new JScrollPane(tree);
        panel.add(treeView);

        return panel;
    }

    private static final class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = -5621219150752332739L;

        private final Icon[] icons;

        private CustomTreeCellRenderer(Icon[] icons) {
            this.icons = icons;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof CustomTreeNode) {
                CustomTreeNode c = (CustomTreeNode) value;
                setIcon(icons[c.node.getMaximumWarningLevel().ordinal()]);
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
        public final Node node;
        public final List<CustomTreeNode> displayedChildren = new ArrayList<>();

        public CustomTreeNode(TreeNode parent, Node node, WarningLevel minimumWarningLevel) {
            this.parent = parent;
            this.node = node;
            for (Node c : node.children) {
                if (minimumWarningLevel.isWorseThan(c.getMaximumWarningLevel())) {
                    continue;
                }
                displayedChildren.add(new CustomTreeNode(this, c, minimumWarningLevel));
            }
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
        public Enumeration children() {
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
