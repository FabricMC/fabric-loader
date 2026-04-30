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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.LookAndFeel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiData;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiIconSource;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiDependency;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiMod;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiRequirement;
import net.fabricmc.loader.impl.gui.FabricStatusTree.DependencyGuiSuggestedChange;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricBasicButtonType;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusButton;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricTreeWarningLevel;
import net.fabricmc.loader.impl.util.Localization;
import net.fabricmc.loader.impl.util.StringUtil;

class FabricMainWindow {
	static Icon missingIcon = null;
	private static final Map<String, Icon> modIconCache = new HashMap<>();
	private static Map<String, DependencyGuiIconSource> dependencyGuiIconSources = java.util.Collections.emptyMap();
	private static JComponent suggestedChangesSection;

	private static final Color ERROR = new Color(232, 65, 75);
	private static final Color INFO = new Color(38, 112, 218);
	private static final int PAGE_MARGIN = 32;

	static void open(FabricStatusTree tree, boolean shouldWait) throws Exception {
		if (GraphicsEnvironment.isHeadless()) {
			throw new HeadlessException();
		}

		// Set macOS specific system props
		System.setProperty("apple.awt.application.appearance", "system");
		System.setProperty("apple.awt.application.name", tree.title);

		setupLookAndFeel();

		open0(tree, shouldWait);
	}

	private static void setupLookAndFeel() {
		ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

		try {
			LookAndFeel lookAndFeel = createFlatLookAndFeel();
			ClassLoader flatLafClassLoader = lookAndFeel.getClass().getClassLoader();

			Thread.currentThread().setContextClassLoader(flatLafClassLoader);
			UIManager.setLookAndFeel(lookAndFeel);
			setUiDefaultsClassLoader(flatLafClassLoader);

			if (!isLookAndFeelUsable()) {
				throw new IllegalStateException("FlatLaf did not install complete Swing UI defaults");
			}
		} catch (Throwable t) {
			setupFallbackLookAndFeel(oldContextClassLoader);
		} finally {
			Thread.currentThread().setContextClassLoader(oldContextClassLoader);
		}
	}

	private static LookAndFeel createFlatLookAndFeel() {
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		boolean macOS = osName.contains("mac") || osName.contains("darwin");
		boolean darkMode = Boolean.parseBoolean(System.getProperty("fabric.loader.gui.darkMode"));

		if (macOS) {
			return darkMode ? new FlatMacDarkLaf() : new FlatMacLightLaf();
		}

		return darkMode ? new FlatDarkLaf() : new FlatLightLaf();
	}

	private static void setupFallbackLookAndFeel(ClassLoader contextClassLoader) {
		try {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

			if (!isLookAndFeelUsable()) {
				UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
			}
		} catch (Throwable ignored) {
			try {
				UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
			} catch (Throwable ignoredAgain) {
				// Swing will report the original problem when creating components.
			}
		}
	}

	private static void setUiDefaultsClassLoader(ClassLoader classLoader) {
		UIDefaults defaults = UIManager.getDefaults();

		if (defaults != null && classLoader != null) {
			defaults.put("ClassLoader", classLoader);
		}
	}

	private static boolean isLookAndFeelUsable() {
		UIDefaults defaults = UIManager.getDefaults();
		return defaults != null
				&& canLoadUiDelegate(defaults, "PanelUI")
				&& canLoadUiDelegate(defaults, "LabelUI")
				&& canLoadUiDelegate(defaults, "ButtonUI")
				&& canLoadUiDelegate(defaults, "RootPaneUI")
				&& UIManager.getFont("Label.font") != null;
	}

	private static boolean canLoadUiDelegate(UIDefaults defaults, String key) {
		Object value = defaults.get(key);

		if (value == null) {
			return false;
		}

		if (!(value instanceof String)) {
			return true;
		}

		ClassLoader classLoader = (ClassLoader) defaults.get("ClassLoader");

		if (classLoader == null) {
			classLoader = Thread.currentThread().getContextClassLoader();
		}

		try {
			Class.forName((String) value, false, classLoader);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static void open0(FabricStatusTree tree, boolean shouldWait) throws Exception {
		CountDownLatch guiTerminatedLatch = new CountDownLatch(1);

		SwingUtilities.invokeAndWait(() -> {
			ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

			try {
				Thread.currentThread().setContextClassLoader(FabricMainWindow.class.getClassLoader());
				createUi(guiTerminatedLatch, tree);
			} finally {
				Thread.currentThread().setContextClassLoader(oldContextClassLoader);
			}
		});

		if (shouldWait) {
			guiTerminatedLatch.await();
		}
	}

	private static void createUi(CountDownLatch onCloseLatch, FabricStatusTree tree) {
		suggestedChangesSection = null;

		JFrame window = new JFrame();
		window.setVisible(false);
		window.setTitle(tree.title);

		try {
			Image image = loadImage("/ui/icon/fabric_x128.png");
			window.setIconImage(image);
			setTaskBarImage(image);
		} catch (IOException e) {
			e.printStackTrace();
		}

		window.setMinimumSize(new Dimension(720, 500));
		window.setPreferredSize(new Dimension(980, 640));
		window.setLocationByPlatform(true);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				onCloseLatch.countDown();
			}
		});

		Container contentPane = window.getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(createHeader(tree), BorderLayout.NORTH);
		contentPane.add(createMainContent(tree), BorderLayout.CENTER);

		if (!tree.buttons.isEmpty()) {
			contentPane.add(createButtonPanel(window, onCloseLatch, tree.buttons), BorderLayout.SOUTH);
		}

		sizeWindowToShowSuggestedChanges(window);
		window.setVisible(true);
		window.requestFocus();
	}

	private static void sizeWindowToShowSuggestedChanges(JFrame window) {
		window.pack();

		Dimension min = new Dimension(980, 640);
		window.setSize(Math.max(window.getWidth(), min.width), Math.max(window.getHeight(), min.height));

		if (suggestedChangesSection == null) {
			return;
		}

		SwingUtilities.invokeLater(() -> {
			JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, suggestedChangesSection);

			if (scrollPane == null) {
				return;
			}

			Rectangle sectionBounds = SwingUtilities.convertRectangle(
					suggestedChangesSection.getParent(),
					suggestedChangesSection.getBounds(),
					scrollPane.getViewport().getView());
			int visibleBottom = scrollPane.getViewport().getViewPosition().y + scrollPane.getViewport().getExtentSize().height;
			int neededExtra = sectionBounds.y + sectionBounds.height - visibleBottom;

			if (neededExtra <= 0) {
				return;
			}

			GraphicsConfiguration gc = window.getGraphicsConfiguration();
			Rectangle screen = gc.getBounds();
			Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
			int maxHeight = screen.height - insets.top - insets.bottom - 80;
			int newHeight = Math.min(window.getHeight() + neededExtra, maxHeight);

			if (newHeight > window.getHeight()) {
				window.setSize(window.getWidth(), newHeight);
			}
		});
	}

	private static JPanel createHeader(FabricStatusTree tree) {
		JPanel header = new JPanel(new BorderLayout(18, 0));
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
				BorderFactory.createEmptyBorder(26, PAGE_MARGIN, 26, PAGE_MARGIN)));

		JLabel icon = new JLabel(new ErrorIcon(66));
		header.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JLabel title = new JLabel(stripHtml(tree.mainText == null || tree.mainText.isEmpty() ? tree.title : tree.mainText));
		title.setFont(deriveFont(title, Font.BOLD, 2.0f));
		text.add(title);
		text.add(Box.createVerticalStrut(6));

		JLabel subtitle = new JLabel(Localization.format(isIncompatibleMods(tree)
				? "gui.dependency.subtitle.incompatible"
				: "gui.dependency.subtitle.generic"));
		subtitle.setFont(deriveFont(subtitle, 1.2f));
		subtitle.setForeground(secondaryTextColor());
		text.add(subtitle);

		header.add(text, BorderLayout.CENTER);
		return header;
	}

	private static Component createMainContent(FabricStatusTree tree) {
		DependencyGuiData structuredData = tree.getDependencyGuiData();
		dependencyGuiIconSources = structuredData != null ? structuredData.iconSources : java.util.Collections.emptyMap();

		if (structuredData != null) {
			DependencyUiData data = DependencyUiData.from(structuredData);

			if (data.hasContent()) {
				return createDependencyPanel(data);
			}
		}

		return createGeneralPanel(tree);
	}

	private static Component createDependencyPanel(DependencyUiData data) {
		if (!data.modIssues.isEmpty()) {
			return createModIssueBrowser(data);
		}

		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setBorder(BorderFactory.createEmptyBorder(28, PAGE_MARGIN, 28, PAGE_MARGIN));

		int section = 1;

		if (!data.otherActions.isEmpty()) {
			JComponent suggestedSection = leftAligned(createNumberedSection(section++, Localization.format("gui.dependency.section.suggestedChanges"),
					Localization.format("gui.dependency.section.suggestedChanges.desc"), createActionRows(data)));
			suggestedChangesSection = suggestedSection;
			page.add(suggestedSection);
		}

		if (!data.dependencies.isEmpty()) {
			if (section > 1) {
				page.add(createSectionGap());
				page.add(createPageSeparator());
				page.add(createSectionGap());
			}

			page.add(leftAligned(createNumberedSection(section++, Localization.format("gui.dependency.section.whatsMissing"),
					Localization.format("gui.dependency.section.whatsMissing.desc"), createDependencyRows(data))));
		}

		if (!data.dependants.isEmpty()) {
			if (section > 1) {
				page.add(createSectionGap());
				page.add(createPageSeparator());
				page.add(createSectionGap());
			}

			page.add(leftAligned(createNumberedSection(section, Localization.format("gui.dependency.section.whoNeedsIt"),
					Localization.format("gui.dependency.section.whoNeedsIt.desc"), createDependantRows(data))));
		}

		return wrapScrollable(page);
	}

	private static Component createModIssueBrowser(DependencyUiData data) {
		CardLayout layout = new CardLayout();
		JPanel cards = new JPanel(layout);
		Map<String, String> cardNames = new LinkedHashMap<>();

		for (ModIssue issue : data.modIssues.values()) {
			cardNames.put(issue.getKey(), "detail-" + cardNames.size());
		}

		cards.add(wrapScrollable(createModIssueOverviewPage(data, issue -> layout.show(cards, cardNames.get(issue.getKey())))), "overview");

		for (ModIssue issue : data.modIssues.values()) {
			cards.add(wrapScrollable(createModIssueDetailPage(issue, () -> layout.show(cards, "overview"))), cardNames.get(issue.getKey()));
		}

		return cards;
	}

	private static JScrollPane wrapScrollable(Component component) {
		JScrollPane scrollPane = new JScrollPane(component);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		return scrollPane;
	}

	private static JPanel createModIssueOverviewPage(DependencyUiData data, Consumer<ModIssue> onSelect) {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setBorder(BorderFactory.createEmptyBorder(28, PAGE_MARGIN, 28, PAGE_MARGIN));

		int section = 1;

		if (!data.otherActions.isEmpty()) {
			JComponent suggestedSection = leftAligned(createNumberedSection(section++, Localization.format("gui.dependency.section.suggestedChanges"),
					Localization.format("gui.dependency.section.suggestedChanges.desc"), createActionRows(data)));
			suggestedChangesSection = suggestedSection;
			page.add(suggestedSection);
		}

		if (section > 1) {
			page.add(createSectionGap());
			page.add(createPageSeparator());
			page.add(createSectionGap());
		}

		page.add(leftAligned(createNumberedSection(section++, Localization.format("gui.dependency.section.affectedMods"),
				Localization.format("gui.dependency.section.affectedMods.desc"), createModIssueRows(data, onSelect))));

		if (!data.dependencies.isEmpty()) {
			page.add(createSectionGap());
			page.add(createPageSeparator());
			page.add(createSectionGap());
			page.add(leftAligned(createNumberedSection(section, Localization.format("gui.dependency.section.missingOverview"),
					Localization.format("gui.dependency.section.missingOverview.desc"), createDependencyRows(data))));
		}

		return page;
	}

	private static JPanel createModIssueRows(DependencyUiData data, Consumer<ModIssue> onSelect) {
		JPanel rows = createRowsPanel();

		for (ModIssue issue : data.modIssues.values()) {
			RoundedPanel row = createCardPanel();
			row.setLayout(new BorderLayout(16, 0));
			row.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

			JPanel left = new JPanel(new BorderLayout(14, 0));
			left.setOpaque(false);
			Icon modIcon = loadModIcon(issue.modId, 34);
			left.add(new JLabel(modIcon != null ? modIcon : new DocumentIcon(34)), BorderLayout.WEST);

			JPanel text = new JPanel();
			text.setOpaque(false);
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

			JLabel name = new JLabel(html("<b>" + escape(issue.modDisplayName) + "</b>"
					+ (issue.modId.isEmpty() ? "" : " <span style='color:" + colorToHex(secondaryTextColor()) + "'>(" + escape(issue.modId) + ")</span>")));
			name.setFont(deriveFont(name, 1.07f));
			text.add(name);
			text.add(Box.createVerticalStrut(4));

			JLabel summary = new JLabel(issue.getSummaryText());
			summary.setFont(deriveFont(summary, 1.0f));
			summary.setForeground(secondaryTextColor());
			text.add(summary);

			left.add(text, BorderLayout.CENTER);
			row.add(left, BorderLayout.CENTER);

			JPanel right = new JPanel();
			right.setOpaque(false);
			right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

			if (!issue.modVersion.isEmpty()) {
				JLabel pill = createPill(issue.modVersion);
				pill.setAlignmentX(Component.RIGHT_ALIGNMENT);
				right.add(pill);
				right.add(Box.createVerticalStrut(10));
			}

			JButton detailsButton = createDetailButton(Localization.format("gui.dependency.button.details"));
			detailsButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
			detailsButton.addActionListener(event -> onSelect.accept(issue));
			right.add(detailsButton);

			row.add(right, BorderLayout.EAST);
			attachClickHandler(row, () -> onSelect.accept(issue));

			rows.add(row);
			rows.add(Box.createVerticalStrut(8));
		}

		trimTrailingSpacer(rows);
		return rows;
	}

	private static JPanel createModIssueDetailPage(ModIssue issue, Runnable onBack) {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setBorder(BorderFactory.createEmptyBorder(28, PAGE_MARGIN, 28, PAGE_MARGIN));

		JButton backButton = createSecondaryButton(Localization.format("gui.dependency.button.back"));
		backButton.addActionListener(event -> onBack.run());
		page.add(leftAligned(backButton));
		page.add(Box.createVerticalStrut(18));

		RoundedPanel hero = createCardPanel();
		hero.setLayout(new BorderLayout(16, 0));
		hero.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

		JPanel heroLeft = new JPanel(new BorderLayout(14, 0));
		heroLeft.setOpaque(false);
		Icon modIcon = loadModIcon(issue.modId, 42);
		heroLeft.add(new JLabel(modIcon != null ? modIcon : new DocumentIcon(42)), BorderLayout.WEST);

		JPanel heroText = new JPanel();
		heroText.setOpaque(false);
		heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));

		JLabel heroTitle = new JLabel(html("<b>" + escape(issue.modDisplayName) + "</b>"
				+ (issue.modId.isEmpty() ? "" : " <span style='color:" + colorToHex(secondaryTextColor()) + "'>(" + escape(issue.modId) + ")</span>")));
		heroTitle.setFont(deriveFont(heroTitle, Font.BOLD, 1.50f));
		heroText.add(heroTitle);
		heroText.add(Box.createVerticalStrut(6));

		JLabel heroSubtitle = new JLabel(issue.getDetailSubtitle());
		heroSubtitle.setFont(deriveFont(heroSubtitle, 1.02f));
		heroSubtitle.setForeground(secondaryTextColor());
		heroText.add(heroSubtitle);

		heroLeft.add(heroText, BorderLayout.CENTER);
		hero.add(heroLeft, BorderLayout.CENTER);

		if (!issue.modVersion.isEmpty()) {
			JLabel pill = createPill(issue.modVersion);
			pill.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(borderColor()),
					BorderFactory.createEmptyBorder(8, 12, 8, 12)));
			hero.add(pill, BorderLayout.EAST);
		}

		page.add(leftAligned(hero));
		page.add(Box.createVerticalStrut(24));
		page.add(leftAligned(createDetailSection(Localization.format("gui.dependency.section.requiredDependencies"),
				Localization.format("gui.dependency.section.requiredDependencies.desc"), createModRequirementRows(issue))));

		return page;
	}

	private static JPanel createModRequirementRows(ModIssue issue) {
		JPanel rows = createRowsPanel();

		for (DependencyRequirement requirement : issue.getGroupedRequirements().values()) {
			RoundedPanel row = createCardPanel();
			row.setLayout(new BorderLayout(16, 0));
			row.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

			JPanel left = new JPanel(new BorderLayout(14, 0));
			left.setOpaque(false);
			Icon requirementIcon = loadModIcon(requirement.id, 30);
			left.add(new JLabel(requirementIcon != null ? requirementIcon : new DocumentIcon(30)), BorderLayout.WEST);

			JPanel text = new JPanel();
			text.setOpaque(false);
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

			JLabel name = new JLabel(requirement.displayName);
			name.setFont(deriveFont(name, Font.BOLD, 1.10f));
			text.add(name);

			for (String version : requirement.versions) {
				text.add(Box.createVerticalStrut(4));
				JLabel line = new JLabel(html("<span style='color:" + colorToHex(ERROR) + "'>" + escape(formatVersionText(version)) + "</span>"));
				line.setFont(deriveFont(line, 0.98f));
				text.add(line);
			}

			left.add(text, BorderLayout.CENTER);
			row.add(left, BorderLayout.CENTER);

			rows.add(row);
			rows.add(Box.createVerticalStrut(8));
		}

		trimTrailingSpacer(rows);
		return rows;
	}

	private static JPanel createNumberedSection(int number, String title, String description, Component rows) {
		JPanel section = new JPanel(new BorderLayout(16, 0));
		section.setOpaque(false);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel badge = new JLabel(Integer.toString(number), SwingConstants.CENTER);
		badge.setOpaque(false);
		badge.setForeground(Color.WHITE);
		badge.setFont(deriveFontSize(badge, Font.BOLD, 15f));
		badge.setIcon(new CircleIcon(38, ERROR));
		badge.setHorizontalTextPosition(SwingConstants.CENTER);
		badge.setVerticalTextPosition(SwingConstants.CENTER);
		badge.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		section.add(badge, BorderLayout.WEST);

		JPanel content = new JPanel();
		content.setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(deriveFont(titleLabel, Font.BOLD, 1.30f));
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(titleLabel);
		content.add(Box.createVerticalStrut(6));

		JLabel descriptionLabel = new JLabel(description);
		descriptionLabel.setFont(deriveFont(descriptionLabel, 1.0f));
		descriptionLabel.setForeground(secondaryTextColor());
		descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(descriptionLabel);
		content.add(Box.createVerticalStrut(14));

		if (rows instanceof JComponent) {
			((JComponent) rows).setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		content.add(rows);
		section.add(content, BorderLayout.CENTER);
		return section;
	}

	private static JPanel createDependencyRows(DependencyUiData data) {
		JPanel rows = createRowsPanel();

		for (DependencyRequirement dependency : data.dependencies.values()) {
			RoundedPanel row = createCardPanel();
			row.setLayout(new BorderLayout(16, 0));
			row.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

			JPanel left = new JPanel(new BorderLayout(14, 0));
			left.setOpaque(false);
			Icon dependencyIcon = loadModIcon(dependency.id, 30);
			left.add(new JLabel(dependencyIcon != null ? dependencyIcon : new DocumentIcon(30)), BorderLayout.WEST);

			JLabel name = new JLabel(dependency.displayName);
			name.setFont(deriveFont(name, Font.BOLD, 1.12f));
			left.add(name, BorderLayout.CENTER);
			row.add(left, BorderLayout.CENTER);

			JLabel version = new JLabel(html("<span style='color:" + colorToHex(ERROR) + "'>" + escape(formatVersionText(dependency.getSummaryVersion())) + "</span>"));
			version.setFont(deriveFont(version, 1.0f));
			row.add(version, BorderLayout.EAST);

			rows.add(row);
			rows.add(Box.createVerticalStrut(8));
		}

		trimTrailingSpacer(rows);
		return rows;
	}

	private static JPanel createDependantRows(DependencyUiData data) {
		JPanel rows = createRowsPanel();

		for (DependantRequirement dependant : data.dependants) {
			RoundedPanel row = createCardPanel();
			row.setBackground(tintedColor(ERROR, 0.06f));
			row.setBorderColor(tintedColor(ERROR, 0.22f));
			row.setLayout(new BorderLayout(16, 0));
			row.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

			JPanel left = new JPanel(new BorderLayout(14, 0));
			left.setOpaque(false);
			Icon modIcon = loadModIcon(dependant.modId, 34);
			left.add(new JLabel(modIcon != null ? modIcon : new DocumentIcon(34)), BorderLayout.WEST);

			JPanel text = new JPanel();
			text.setOpaque(false);
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

			JLabel name = new JLabel(html("<b>" + escape(dependant.modDisplayName) + "</b>"
					+ (dependant.modId.isEmpty() ? "" : " <span style='color:" + colorToHex(secondaryTextColor()) + "'>(" + escape(dependant.modId) + ")</span>")));
			name.setFont(deriveFont(name, 1.05f));
			text.add(name);
			text.add(Box.createVerticalStrut(3));

			JLabel requires = new JLabel(html(escape(Localization.format("gui.dependency.requires")) + " <span style='color:" + colorToHex(ERROR) + "'>" + escape(dependant.dependencyDisplayName) + "</span>"
					+ (dependant.requiredVersion.isEmpty() ? "" : " <span style='color:" + colorToHex(secondaryTextColor()) + "'>(" + escape(formatVersionText(dependant.requiredVersion)) + ")</span>")));
			requires.setFont(deriveFont(requires, 1.0f));
			text.add(requires);

			left.add(text, BorderLayout.CENTER);
			row.add(left, BorderLayout.CENTER);

			if (!dependant.modVersion.isEmpty()) {
				JLabel pill = createPill(dependant.modVersion);
				row.add(pill, BorderLayout.EAST);
			}

			rows.add(row);
			rows.add(Box.createVerticalStrut(8));
		}

		trimTrailingSpacer(rows);
		return rows;
	}

	private static JPanel createActionRows(DependencyUiData data) {
		JPanel rows = createRowsPanel();

		for (String action : data.otherActions) {
			RoundedPanel row = createCardPanel();
			row.setLayout(new BorderLayout(12, 0));
			row.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
			row.add(new JLabel(getActionIcon(data.actionTargetIds.get(action), 24)), BorderLayout.WEST);
			row.add(new JLabel(action), BorderLayout.CENTER);
			rows.add(row);
			rows.add(Box.createVerticalStrut(8));
		}

		trimTrailingSpacer(rows);
		return rows;
	}

	private static Icon getActionIcon(String targetId, int size) {
		if (targetId != null && !targetId.isEmpty()) {
			Icon icon = loadModIcon(targetId, size);

			if (icon != null) {
				return icon;
			}
		}

		return new DocumentIcon(size);
	}

	private static JPanel createDetailSection(String title, String description, Component rows) {
		JPanel section = new JPanel();
		section.setOpaque(false);
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(deriveFont(titleLabel, Font.BOLD, 1.30f));
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(titleLabel);
		section.add(Box.createVerticalStrut(6));

		JLabel descriptionLabel = new JLabel(description);
		descriptionLabel.setFont(deriveFont(descriptionLabel, 1.0f));
		descriptionLabel.setForeground(secondaryTextColor());
		descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(descriptionLabel);
		section.add(Box.createVerticalStrut(14));

		if (rows instanceof JComponent) {
			((JComponent) rows).setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		section.add(rows);
		return section;
	}

	private static JPanel createRowsPanel() {
		JPanel rows = new JPanel();
		rows.setOpaque(false);
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		return rows;
	}

	private static RoundedPanel createCardPanel() {
		RoundedPanel panel = new RoundedPanel(10);
		panel.setBackground(cardColor());
		panel.setBorderColor(borderColor());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel createPill(String text) {
		JLabel label = new JLabel(text);
		label.setOpaque(true);
		label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(borderColor()),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		label.setBackground(backgroundColor());
		return label;
	}

	private static JButton createSecondaryButton(String text) {
		JButton button = new JButton(text);
		button.putClientProperty("JButton.buttonType", "roundRect");
		button.putClientProperty("JButton.arc", 999);
		button.putClientProperty("JComponent.minimumWidth", 132);
		button.setFocusable(false);
		return button;
	}

	private static JButton createDetailButton(String text) {
		JButton button = createSecondaryButton(text);
		button.putClientProperty("JComponent.minimumWidth", 114);
		return button;
	}

	private static JSeparator createPageSeparator() {
		JSeparator separator = new JSeparator();
		separator.setAlignmentX(Component.LEFT_ALIGNMENT);
		return separator;
	}

	private static Component createSectionGap() {
		return Box.createVerticalStrut(24);
	}

	private static void attachClickHandler(Component component, Runnable action) {
		if (!(component instanceof JButton)) {
			component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			component.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					action.run();
				}
			});
		}

		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				attachClickHandler(child, action);
			}
		}
	}

	private static <T extends JComponent> T leftAligned(T component) {
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}

	private static void trimTrailingSpacer(JPanel panel) {
		int count = panel.getComponentCount();

		if (count > 0) {
			panel.remove(count - 1);
		}
	}

	private static Component createGeneralPanel(FabricStatusTree tree) {
		IconSet icons = new IconSet();

		if (tree.tabs.isEmpty()) {
			FabricStatusTab tab = new FabricStatusTab(Localization.format("gui.error.openingErrors"));
			tab.addChild(Localization.format("gui.error.noTabs")).setError();
			return createTreePanel(tab.node, tab.filterLevel, icons);
		} else if (tree.tabs.size() == 1) {
			FabricStatusTab tab = tree.tabs.get(0);
			return createTreePanel(tab.node, tab.filterLevel, icons);
		} else {
			JTabbedPane tabs = new JTabbedPane();

			for (FabricStatusTab tab : tree.tabs) {
				tabs.addTab(tab.node.name, createTreePanel(tab.node, tab.filterLevel, icons));
			}

			return tabs;
		}
	}

	private static JPanel createButtonPanel(JFrame window, CountDownLatch onCloseLatch, List<FabricStatusButton> sourceButtons) {
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()),
				BorderFactory.createEmptyBorder(18, PAGE_MARGIN, 18, PAGE_MARGIN)));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		left.setOpaque(false);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 10, 0));
		right.setOpaque(false);

		for (FabricStatusButton button : sourceButtons) {
			JButton btn = new JButton(button.text);

			if (button.clipboard != null) {
				btn.setIcon(new ClipboardIcon(18));
			}

			btn.addActionListener(event -> {
				if (button.type == FabricBasicButtonType.CLICK_ONCE) btn.setEnabled(false);

				if (button.clipboard != null) {
					try {
						StringSelection clipboard = new StringSelection(button.clipboard);
						Toolkit.getDefaultToolkit().getSystemClipboard().setContents(clipboard, clipboard);
					} catch (IllegalStateException e) {
						// Clipboard unavailable?
					}
				}

				if (button.shouldClose) {
					window.dispose();
				}

				if (button.shouldContinue) {
					onCloseLatch.countDown();
				}
			});

			if (button.clipboard != null && !button.shouldClose) {
				left.add(btn);
			} else {
				right.add(btn);
			}
		}

		outer.add(left, BorderLayout.WEST);
		outer.add(right, BorderLayout.EAST);
		return outer;
	}

	private static JPanel createTreePanel(FabricStatusNode rootNode, FabricTreeWarningLevel minimumWarningLevel,
			IconSet iconSet) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(18, PAGE_MARGIN, 18, PAGE_MARGIN));

		TreeNode treeNode = new CustomTreeNode(null, rootNode, minimumWarningLevel);

		DefaultTreeModel model = new DefaultTreeModel(treeNode);
		JTree tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setRowHeight(0); // Allow rows to be multiple lines tall

		for (int row = 0; row < tree.getRowCount(); row++) {
			if (!tree.isVisible(tree.getPathForRow(row))) {
				continue;
			}

			CustomTreeNode node = ((CustomTreeNode) tree.getPathForRow(row).getLastPathComponent());

			if (node.node.expandByDefault) {
				tree.expandRow(row);
			}
		}

		ToolTipManager.sharedInstance().registerComponent(tree);
		tree.setCellRenderer(new CustomTreeCellRenderer(iconSet));

		JScrollPane scrollPane = new JScrollPane(tree);
		scrollPane.setBorder(BorderFactory.createLineBorder(borderColor()));
		panel.add(scrollPane, BorderLayout.CENTER);

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

	private static void setTaskBarImage(Image image) {
		try {
			// TODO Remove reflection when updating past Java 8
			Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
			Method getTaskbar = taskbarClass.getDeclaredMethod("getTaskbar");
			Method setIconImage = taskbarClass.getDeclaredMethod("setIconImage", Image.class);
			Object taskbar = getTaskbar.invoke(null);
			setIconImage.invoke(taskbar, image);
		} catch (Exception e) {
			// Ignored
		}
	}

	private static Font defaultFont() {
		Font font = UIManager.getFont("Label.font");
		return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
	}

	private static Font deriveFont(Component component, float scale) {
		Font font = component.getFont();
		if (font == null) font = defaultFont();
		return font.deriveFont(Math.max(1f, font.getSize2D() * scale));
	}

	private static Font deriveFont(Component component, int style, float scale) {
		Font font = component.getFont();
		if (font == null) font = defaultFont();
		return font.deriveFont(style, Math.max(1f, font.getSize2D() * scale));
	}

	private static Font deriveFontSize(Component component, int style, float size) {
		Font font = component.getFont();
		if (font == null) font = defaultFont();
		return font.deriveFont(style, Math.max(1f, size));
	}

	private static boolean isIncompatibleMods(FabricStatusTree tree) {
		String text = tree.mainText == null ? "" : tree.mainText.toLowerCase();
		return text.contains("incompatible") && text.contains("mod");
	}

	private static Color backgroundColor() {
		Color color = UIManager.getColor("Panel.background");
		return color == null ? Color.WHITE : color;
	}

	private static Color cardColor() {
		Color color = UIManager.getColor("TextField.background");
		return color == null ? backgroundColor() : color;
	}

	private static Color borderColor() {
		Color color = UIManager.getColor("Component.borderColor");
		return color == null ? new Color(210, 214, 220) : color;
	}

	private static Color secondaryTextColor() {
		Color color = UIManager.getColor("Label.disabledForeground");
		return color == null ? new Color(105, 110, 118) : color;
	}

	private static Color tintedColor(Color color, float amount) {
		Color base = backgroundColor();
		int r = Math.min(255, Math.round(base.getRed() * (1.0f - amount) + color.getRed() * amount));
		int g = Math.min(255, Math.round(base.getGreen() * (1.0f - amount) + color.getGreen() * amount));
		int b = Math.min(255, Math.round(base.getBlue() * (1.0f - amount) + color.getBlue() * amount));
		return new Color(r, g, b);
	}

	private static String colorToHex(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private static String formatVersionText(String version) {
		if (version.startsWith("version ")) {
			return Localization.format("gui.dependency.versionPrefix", version.substring("version ".length()));
		}

		return version;
	}

	private static Icon loadModIcon(String modId, int size) {
		if (modId == null || modId.isEmpty()) {
			return null;
		}

		String cacheKey = modId + "@" + size;
		Icon cached = modIconCache.get(cacheKey);

		if (cached != null) {
			return cached;
		}

		Icon icon = findModIcon(modId, size);

		if (icon != null) {
			modIconCache.put(cacheKey, icon);
		}

		return icon;
	}

	private static Icon findModIcon(String modId, int size) {
		Icon bundledIcon = loadBundledModIcon(modId, size);

		if (bundledIcon != null) {
			return bundledIcon;
		}

		DependencyGuiIconSource iconSource = dependencyGuiIconSources.get(modId);

		if (iconSource != null) {
			Icon icon = loadIconFromSerializedSource(iconSource, size);

			if (icon != null) {
				return icon;
			}
		}

		for (ModCandidateImpl candidate : getDiscoveredModCandidates()) {
			if (!modId.equals(candidate.getId())) {
				continue;
			}

			Optional<String> iconPath = candidate.getMetadata().getIconPath(size);

			if (!iconPath.isPresent() || !candidate.hasPath()) {
				continue;
			}

			Icon icon = loadIconFromModPaths(candidate.getPaths(), iconPath.get(), size);

			if (icon != null) {
				return icon;
			}
		}

		return null;
	}

	private static Icon loadBundledModIcon(String modId, int size) {
		if ("minecraft".equals(modId)) {
			return loadBundledIcon("/ui/icon/minecraft_x32.png", size);
		}

		if ("java".equals(modId)) {
			return loadBundledIcon("/ui/icon/java_x32.png", size);
		}

		return null;
	}

	private static Icon loadBundledIcon(String path, int size) {
		try {
			BufferedImage image = loadImage(path);
			return new ImageIcon(scaleImage(image, size));
		} catch (IOException e) {
			return null;
		}
	}

	private static Optional<String> findModDisplayName(String modId) {
		if (modId == null || modId.isEmpty()) {
			return Optional.empty();
		}

		for (ModCandidateImpl candidate : getDiscoveredModCandidates()) {
			if (modId.equals(candidate.getId())) {
				String name = candidate.getMetadata().getName();

				if (name != null && !name.isEmpty()) {
					return Optional.of(name);
				}

				return Optional.of(candidate.getId());
			}
		}

		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private static List<ModCandidateImpl> getDiscoveredModCandidates() {
		try {
			Field field = FabricLoaderImpl.class.getDeclaredField("modCandidates");
			field.setAccessible(true);
			Object value = field.get(FabricLoaderImpl.INSTANCE);

			if (value instanceof List) {
				return (List<ModCandidateImpl>) value;
			}
		} catch (Throwable ignored) {
			// The GUI can also run in a forked process where discovered mod candidates are unavailable.
		}

		return java.util.Collections.emptyList();
	}

	private static Icon loadIconFromSerializedSource(DependencyGuiIconSource iconSource, int size) {
		if (iconSource.iconBytes.length > 0) {
			try {
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(iconSource.iconBytes));

				if (image != null) {
					return new ImageIcon(scaleImage(image, size));
				}
			} catch (IOException ignored) {
				// Fall back to path based loading below.
			}
		}

		List<Path> paths = new ArrayList<>();

		for (String path : iconSource.paths) {
			if (path != null && !path.isEmpty()) {
				paths.add(java.nio.file.Paths.get(path));
			}
		}

		return loadIconFromModPaths(paths, iconSource.iconPath, size);
	}

	private static Icon loadIconFromModPaths(List<Path> paths, String iconPath, int size) {
		String normalizedIconPath = iconPath.replace('\\', '/');

		for (Path path : paths) {
			try {
				BufferedImage image;

				if (Files.isDirectory(path)) {
					Path resolvedIconPath = path;

					for (String part : normalizedIconPath.split("/")) {
						if (!part.isEmpty()) {
							resolvedIconPath = resolvedIconPath.resolve(part);
						}
					}

					if (!Files.isRegularFile(resolvedIconPath)) {
						continue;
					}

					image = ImageIO.read(resolvedIconPath.toFile());
				} else {
					try (ZipFile zip = new ZipFile(path.toFile())) {
						ZipEntry entry = zip.getEntry(normalizedIconPath);

						if (entry == null) {
							continue;
						}

						try (InputStream input = zip.getInputStream(entry)) {
							image = ImageIO.read(input);
						}
					}
				}

				if (image == null) {
					continue;
				}

				return new ImageIcon(scaleImage(image, size));
			} catch (Throwable ignored) {
				// Invalid, missing or unreadable icons should not prevent the error UI from opening.
			}
		}

		return null;
	}

	private static Image scaleImage(BufferedImage image, int size) {
		if (image.getWidth() == size && image.getHeight() == size) {
			return image;
		}

		return image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
	}

	private static String html(String body) {
		return "<html>" + body + "</html>";
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String stripHtml(String text) {
		return text.replace("<html>", "").replace("</html>", "");
	}

	private static final class DependencyUiData {
		final Map<String, DependencyRequirement> dependencies = new LinkedHashMap<>();
		final Map<String, ModIssue> modIssues = new LinkedHashMap<>();
		final List<DependantRequirement> dependants = new ArrayList<>();
		final List<String> otherActions = new ArrayList<>();
		final Map<String, String> actionTargetIds = new LinkedHashMap<>();

		static DependencyUiData from(DependencyGuiData source) {
			DependencyUiData data = new DependencyUiData();

			for (DependencyGuiSuggestedChange suggestedChange : source.suggestedChanges) {
				data.addSuggestedAction(suggestedChange.text, suggestedChange.targetId);
			}

			for (DependencyGuiDependency dependency : source.dependencies.values()) {
				DependencyRequirement target = data.getOrCreateDependency(dependency.id, dependency.displayName);

				for (String versionRequirement : dependency.versionRequirements) {
					target.addVersion(versionRequirement);
				}
			}

			for (DependencyGuiMod sourceMod : source.affectedMods.values()) {
				ModIssue issue = new ModIssue(sourceMod.displayName, sourceMod.id, sourceMod.version);
				data.modIssues.put(issue.getKey(), issue);

				for (DependencyGuiRequirement sourceRequirement : sourceMod.requirements) {
					DependencyRequirement dependency = data.getOrCreateDependency(sourceRequirement.dependencyId, sourceRequirement.dependencyDisplayName);
					dependency.addVersion(sourceRequirement.versionRequirement);
					issue.addRequirement(sourceRequirement.dependencyId, sourceRequirement.dependencyDisplayName, sourceRequirement.versionRequirement);
					data.dependants.add(new DependantRequirement(sourceMod.displayName, sourceMod.id, sourceMod.version,
							sourceRequirement.dependencyId, sourceRequirement.dependencyDisplayName, sourceRequirement.versionRequirement));
				}
			}

			return data;
		}

		boolean hasContent() {
			return !dependencies.isEmpty() || !modIssues.isEmpty() || !dependants.isEmpty() || !otherActions.isEmpty();
		}

		private void addSuggestedAction(String action, String targetId) {
			if (!otherActions.contains(action)) {
				otherActions.add(action);
			}

			if (targetId != null && !targetId.isEmpty()) {
				actionTargetIds.put(action, targetId);
			}
		}

		private DependencyRequirement getOrCreateDependency(String id, String displayName) {
			displayName = canonicalDisplayName(id, displayName);
			DependencyRequirement existing = dependencies.get(id);

			if (existing == null) {
				existing = new DependencyRequirement(id, displayName, "");
				dependencies.put(id, existing);
			}

			return existing;
		}

		private static String canonicalDisplayName(String id, String fallbackDisplayName) {
			return findModDisplayName(id).map(s -> s + " (" + id + ")").orElse(fallbackDisplayName);
		}
	}

	private static final class DependencyRequirement {
		final String id;
		final String displayName;
		final List<String> versions = new ArrayList<>();

		DependencyRequirement(String id, String displayName, String version) {
			this.id = id;
			this.displayName = displayName;
			addVersion(version);
		}

		void addVersion(String version) {
			if (version != null && !version.isEmpty() && !versions.contains(version)) {
				versions.add(version);
			}
		}

		String getSummaryVersion() {
			if (versions.isEmpty()) return "";
			if (versions.size() == 1) return versions.get(0);
			return Localization.format("gui.dependency.multipleVersionRequirements");
		}
	}

	private static final class RequirementEntry {
		final String dependencyId;
		final String dependencyDisplayName;
		final String requiredVersion;

		RequirementEntry(String dependencyId, String dependencyDisplayName, String requiredVersion) {
			this.dependencyId = dependencyId;
			this.dependencyDisplayName = dependencyDisplayName;
			this.requiredVersion = requiredVersion;
		}
	}

	private static final class ModIssue {
		final String modDisplayName;
		final String modId;
		final String modVersion;
		final List<RequirementEntry> requirements = new ArrayList<>();

		ModIssue(String modDisplayName, String modId, String modVersion) {
			this.modDisplayName = modDisplayName;
			this.modId = modId;
			this.modVersion = modVersion;
		}

		void addRequirement(String dependencyId, String dependencyDisplayName, String requiredVersion) {
			dependencyDisplayName = DependencyUiData.canonicalDisplayName(dependencyId, dependencyDisplayName);

			for (RequirementEntry entry : requirements) {
				if (entry.dependencyId.equals(dependencyId) && entry.requiredVersion.equals(requiredVersion)) {
					return;
				}
			}

			requirements.add(new RequirementEntry(dependencyId, dependencyDisplayName, requiredVersion));
		}

		String getKey() {
			return modId.isEmpty() ? modDisplayName + "@" + modVersion : modId;
		}

		String getSummaryText() {
			if (requirements.isEmpty()) return Localization.format("gui.dependency.mod.noDetails");
			if (requirements.size() == 1) return Localization.format("gui.dependency.mod.summary.one", requirements.get(0).dependencyDisplayName);
			if (requirements.size() == 2) return Localization.format("gui.dependency.mod.summary.two", requirements.get(0).dependencyDisplayName, requirements.get(1).dependencyDisplayName);
			return Localization.format("gui.dependency.mod.summary.many", requirements.size(), requirements.get(0).dependencyDisplayName, requirements.get(1).dependencyDisplayName, requirements.size() - 2);
		}

		String getDetailSubtitle() {
			return requirements.size() == 1 ? Localization.format("gui.dependency.mod.detailSubtitle.one")
					: Localization.format("gui.dependency.mod.detailSubtitle.many", requirements.size());
		}

		Map<String, DependencyRequirement> getGroupedRequirements() {
			Map<String, DependencyRequirement> grouped = new LinkedHashMap<>();

			for (RequirementEntry entry : requirements) {
				DependencyRequirement existing = grouped.get(entry.dependencyId);

				if (existing == null) {
					existing = new DependencyRequirement(entry.dependencyId, entry.dependencyDisplayName, entry.requiredVersion);
					grouped.put(entry.dependencyId, existing);
				} else {
					existing.addVersion(entry.requiredVersion);
				}
			}

			return grouped;
		}
	}

	private static final class DependantRequirement {
		final String modDisplayName;
		final String modId;
		final String modVersion;
		final String dependencyId;
		final String dependencyDisplayName;
		final String requiredVersion;

		DependantRequirement(String modDisplayName, String modId, String modVersion, String dependencyId, String dependencyDisplayName, String requiredVersion) {
			this.modDisplayName = modDisplayName;
			this.modId = modId;
			this.modVersion = modVersion;
			this.dependencyId = dependencyId;
			this.dependencyDisplayName = dependencyDisplayName;
			this.requiredVersion = requiredVersion;
		}
	}

	private static final class RoundedPanel extends JPanel {
		private static final long serialVersionUID = -2742482680797954853L;

		private final int arc;
		private Color borderColor = borderColor();

		RoundedPanel(int arc) {
			this.arc = arc;
			setOpaque(false);
		}

		void setBorderColor(Color borderColor) {
			this.borderColor = borderColor;
		}

		@Override
		public Dimension getMaximumSize() {
			Dimension preferred = getPreferredSize();
			return new Dimension(Integer.MAX_VALUE, preferred.height);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
			g2.setColor(borderColor);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	private static final class ErrorIcon implements Icon {
		private final int size;

		ErrorIcon(int size) {
			this.size = size;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ERROR);
			g2.fillOval(x, y, size, size);
			g2.setColor(Color.WHITE);
			g2.setStroke(new BasicStroke(Math.max(3, size / 14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int cx = x + size / 2;
			g2.drawLine(cx, y + size / 4, cx, y + size * 3 / 5);
			g2.fillOval(cx - size / 22, y + size * 11 / 16, size / 11, size / 11);
			g2.dispose();
		}
	}

	private static final class CircleIcon implements Icon {
		private final int size;
		private final Color color;

		CircleIcon(int size, Color color) {
			this.size = size;
			this.color = color;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fillOval(x, y, size, size);
			g2.dispose();
		}
	}

	private static final class DocumentIcon implements Icon {
		private final int size;

		DocumentIcon(int size) {
			this.size = size;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(secondaryTextColor());
			int w = size * 4 / 5;
			int left = x + (size - w) / 2;
			int fold = size / 4;
			g2.fillRoundRect(left, y, w, size, 3, 3);
			g2.setColor(cardColor());
			int[] xs = new int[] { left + w - fold, left + w, left + w };
			int[] ys = new int[] { y, y + fold, y };
			g2.fillPolygon(xs, ys, 3);
			g2.dispose();
		}
	}

	private static final class ClipboardIcon implements Icon {
		private final int size;

		ClipboardIcon(int size) {
			this.size = size;
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(INFO);
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawRoundRect(x + size / 4, y + size / 6, size * 3 / 5, size * 3 / 4, 2, 2);
			g2.drawRoundRect(x + size / 8, y + size / 3, size * 3 / 5, size * 3 / 4, 2, 2);
			g2.dispose();
		}
	}

	static final class IconSet {
		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<IconInfo, Map<Integer, Icon>> icons = new HashMap<>();

		public Icon get(IconInfo info) {
			// TODO: HDPI

			int scale = 16;
			Map<Integer, Icon> map = icons.computeIfAbsent(info, k -> new HashMap<>());

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
			//setVerticalTextPosition(TOP); // Move icons to top rather than centre
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			if (value instanceof CustomTreeNode) {
				CustomTreeNode c = (CustomTreeNode) value;
				setIcon(iconSet.get(c.getIconInfo()));

				if (c.node.details == null || c.node.details.isEmpty()) {
					setToolTipText(null);
				} else {
					setToolTipText(applyWrapping(c.node.details));
				}
			}

			return this;
		}
	}

	private static String applyWrapping(String str) {
		if (str.indexOf('\n') < 0) {
			return str;
		}

		str = str.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\n", "<br>");

		return "<html>" + str + "</html>";
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
			return applyWrapping(StringUtil.wrapLines(node.name, 120));
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
				final Iterator<CustomTreeNode> it = displayedChildren.iterator();

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
