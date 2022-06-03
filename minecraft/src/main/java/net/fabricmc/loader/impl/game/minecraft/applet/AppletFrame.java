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

package net.fabricmc.loader.impl.game.minecraft.applet;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;

import net.fabricmc.loader.impl.util.Arguments;

/**
 * PLEASE NOTE:
 *
 * <p>This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 * <p>It has been adapted here for the purposes of the Fabric loader.
 */
@SuppressWarnings("serial")
public class AppletFrame extends Frame implements WindowListener {
	private AppletLauncher applet = null;

	public AppletFrame(String title, ImageIcon icon) {
		super(title);

		if (icon != null) {
			Image source = icon.getImage();
			int w = source.getWidth(null);
			int h = source.getHeight(null);

			if (w == -1) {
				w = 32;
				h = 32;
			}

			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = (Graphics2D) image.getGraphics();
			g2d.drawImage(source, 0, 0, null);
			setIconImage(image);
			g2d.dispose();
		}

		addWindowListener(this);
	}

	public void launch(String[] args) {
		Arguments arguments = new Arguments();
		arguments.parse(args);

		String username = arguments.getOrDefault("username", "Player");
		String sessionid;

		if (arguments.containsKey("session") /* 1.6 */) {
			sessionid = arguments.get("session");
		} else if (arguments.getExtraArgs().size() == 2 /* pre 1.6 */) {
			username = arguments.getExtraArgs().get(0);
			sessionid = arguments.getExtraArgs().get(1);
		} else /* fallback */ {
			sessionid = "";
		}

		File instance = new File(arguments.getOrDefault("gameDir", "."));

		String targetDir = System.getProperty("minecraft.applet.TargetDirectory");

		if (targetDir == null) {
			System.setProperty("minecraft.applet.TargetDirectory", instance.toString());
		} else {
			instance = new File(targetDir);
		}

		// 1.3 ~ 1.5 FML
		System.setProperty("minecraft.applet.WrapperClass", AppletLauncher.class.getName());

		boolean doConnect = arguments.containsKey("server") && arguments.containsKey("port");
		String host = "";
		String port = "";

		if (doConnect) {
			host = arguments.get("server");
			port = arguments.get("port");
		}

		boolean fullscreen = arguments.getExtraArgs().contains("--fullscreen");
		boolean demo = arguments.getExtraArgs().contains("--demo");
		int width = Integer.parseInt(arguments.getOrDefault("width", "854"));
		int height = Integer.parseInt(arguments.getOrDefault("height", "480"));

		applet = new AppletLauncher(
				instance,
				username, sessionid,
				host, port, doConnect,
				fullscreen, demo
				);

		for (String key : arguments.keys()) {
			applet.getParams().put("fabric.arguments." + key, arguments.get(key));
		}

		this.add(applet);
		applet.setPreferredSize(new Dimension(width, height));
		pack();
		setLocationRelativeTo(null);
		setResizable(true);
		validate();
		applet.init();
		applet.start();
		setVisible(true);
	}

	@Override
	public void windowClosing(WindowEvent e) {
		Thread shutdownListenerThread = new Thread(new AppletForcedShutdownListener(30000L));
		shutdownListenerThread.setDaemon(true);
		shutdownListenerThread.start();

		if (applet != null) {
			applet.stop();
			applet.destroy();
		}
	}

	@Override
	public void windowOpened(WindowEvent e) { }

	@Override
	public void windowActivated(WindowEvent e) { }

	@Override
	public void windowClosed(WindowEvent e) { }

	@Override
	public void windowIconified(WindowEvent e) { }

	@Override
	public void windowDeiconified(WindowEvent e) { }

	@Override
	public void windowDeactivated(WindowEvent e) { }
}
