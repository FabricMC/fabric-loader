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

import java.io.File;

public final class AppletMain implements Runnable {
	final String[] args;
	private AppletMain(String[] args) {
		this.args = args;
	}

	public static File hookGameDir(File file) {
		File proposed = AppletLauncher.gameDir;

		if (proposed != null) {
			return proposed;
		} else {
			return file;
		}
	}

	public static void main(String[] args) {
		java.awt.EventQueue.invokeLater(new AppletMain(args));
	}

	@Override
	public void run() {
		AppletFrame me = new AppletFrame("Minecraft", null);
		me.launch(args);
	}
}
