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

package net.fabricmc.loader.entrypoint.rubydung;

import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncherBase;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RubyDungLauncher {
	public static File gameDir;

	private final Map<String, String> params;
	private Runnable mcApplet;
	private boolean active;

	public RubyDungLauncher (File instance, String username, String sessionid, String host, String port, boolean doConnect, boolean fullscreen, boolean demo) {
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
			mcApplet = (Runnable) FabricLauncherBase.getLauncher().getTargetClassLoader().loadClass(EntrypointTransformer.appletMainClass)
				                    .getDeclaredConstructor().newInstance();
			//noinspection ConstantConditions
			if (mcApplet == null) {
				throw new RuntimeException("Could not instantiate MinecraftApplet - is null?");
			}

			mcApplet.run();
		} catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getParams() {
		return params;
	}

	private URL pathToUrl(File path) {
		try {
			return path.toURI().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void closeWindow(int status) {
		System.exit(status);
	}
}
