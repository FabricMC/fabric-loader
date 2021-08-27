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

/**
 * PLEASE NOTE:
 *
 * <p>This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 * <p>It has been adapted here for the purposes of the Fabric loader.
 */
class AppletForcedShutdownListener implements Runnable {
	private final long duration;

	AppletForcedShutdownListener(long duration) {
		this.duration = duration;
	}

	@Override
	public void run() {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}

		System.out.println("~~~ Forcing exit! ~~~");
		System.exit(0);
	}
}
