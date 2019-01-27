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

package net.fabricmc.loader.entrypoint;

import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;

import java.applet.Applet;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * PLEASE NOTE:
 *
 * This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 * It has been adapted here for the purposes of the Fabric loader.
 */
public class AppletLauncher extends Applet implements AppletStub {
	public static File gameDir;

	private final Map<String, String> params;
	private Applet mcApplet;
	private boolean active;

	public AppletLauncher(File instance, String username, String sessionid, String host, String port, boolean doConnect) {
		gameDir = instance;

		params = new HashMap<>();
		params.put("username", username);
		params.put("sessionid", sessionid);
		params.put("stand-alone", "true");
		if (doConnect) {
			params.put("server", host);
			params.put("port", port);
		}
		params.put("fullscreen","false"); //Required param for vanilla. Forge handles the absence gracefully.

		try {
			mcApplet = (Applet) FabricLauncherBase.getLauncher().getTargetClassLoader().loadClass(EntrypointTransformer.appletMainClass).newInstance();
			if (mcApplet == null) {
				throw new RuntimeException("Could not instantiate MinecraftApplet - is null?");
			}

			this.add(mcApplet, "Center");
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void replace(Applet applet) {
		this.mcApplet = applet;
		init();
		if (active) {
			start();
			validate();
		}
	}

	private URL pathToUrl(File path) {
		try {
			return path.toURI().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void appletResize(int width, int height) {
		mcApplet.resize(width, height);
	}

	@Override
	public void resize(int width, int height) {
		mcApplet.resize(width, height);
	}

	@Override
	public void resize(Dimension dim) {
		mcApplet.resize(dim);
	}

	@Override
	public String getParameter(String name) {
		String value = params.get(name);
		if (value != null) {
			return value;
		}
		try {
			return super.getParameter(name);
		} catch (Exception ignored) {}
		return null;
	}

	@Override
	public boolean isActive() {
		return this.active;
	}

	@Override
	public void init() {
		mcApplet.setStub(this);
		mcApplet.setSize(getWidth(),getHeight());
		this.setLayout(new BorderLayout());
		this.add(mcApplet, "Center");
		mcApplet.init();
	}

	@Override
	public void start() {
		mcApplet.start();
		active = true;
	}

	@Override
	public void stop() {
		mcApplet.stop();
		active = false;
	}

	@Override
	public URL getCodeBase() {
		try {
			return UrlUtil.asUrl(AppletMain.hookGameDir(new File(".")));
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public URL getDocumentBase() {
		try {
			return new URL("http://www.minecraft.net/game");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void setVisible(boolean flag) {
		super.setVisible(flag);
		mcApplet.setVisible(flag);
	}
}
