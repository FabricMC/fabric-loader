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

import java.applet.Applet;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.impl.game.minecraft.Hooks;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

/**
 * PLEASE NOTE:
 *
 * <p>This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 * <p>It has been adapted here for the purposes of the Fabric loader.
 */
@SuppressWarnings("serial")
public class AppletLauncher extends Applet implements AppletStub {
	public static File gameDir;

	private final Map<String, String> params;
	private Applet mcApplet;
	private boolean active;

	public AppletLauncher(File instance, String username, String sessionid, String host, String port, boolean doConnect, boolean fullscreen, boolean demo) {
		gameDir = instance;

		params = new HashMap<>();
		params.put("username", username);
		params.put("sessionid", sessionid);
		params.put("stand-alone", "true");

		if (doConnect) {
			params.put("server", host);
			params.put("port", port);
		}

		params.put("fullscreen", Boolean.toString(fullscreen)); //Required param for vanilla. Forge handles the absence gracefully.
		params.put("demo", Boolean.toString(demo));

		try {
			mcApplet = (Applet) FabricLauncherBase.getLauncher()
					.getTargetClassLoader()
					.loadClass(Hooks.appletMainClass)
					.getDeclaredConstructor()
					.newInstance();

			//noinspection ConstantConditions
			if (mcApplet == null) {
				throw new RuntimeException("Could not instantiate MinecraftApplet - is null?");
			}

			this.add(mcApplet, "Center");
		} catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getParams() {
		return params;
	}

	// 1.3 ~ 1.5 FML
	public void replace(Applet applet) {
		this.mcApplet = applet;
		init();

		if (active) {
			start();
			validate();
		}
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
		if (value != null) return value;

		try {
			return super.getParameter(name);
		} catch (Exception ignored) {
			// ignored
		}

		return null;
	}

	@Override
	public boolean isActive() {
		return this.active;
	}

	@Override
	public void init() {
		mcApplet.setStub(this);
		mcApplet.setSize(getWidth(), getHeight());
		setLayout(new BorderLayout());
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

	/**
	 * Minecraft 0.30 checks for "minecraft.net" or "www.minecraft.net" being
	 * the applet hosting location, as an anti-rehosting measure. Of course,
	 * being ran stand-alone, it's not actually "hosted" anywhere.
	 *
	 * <p>The side effect of not providing the correct URL here is all levels,
	 * loaded or generated, being set to null.
	 */
	private URL getMinecraftHostingUrl() {
		try {
			return new URL("http://www.minecraft.net/game");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public URL getCodeBase() {
		return getMinecraftHostingUrl();
	}

	@Override
	public URL getDocumentBase() {
		return getMinecraftHostingUrl();
	}

	@Override
	public void setVisible(boolean flag) {
		super.setVisible(flag);
		mcApplet.setVisible(flag);
	}
}
