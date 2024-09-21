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

package net.fabricmc.test.autotest;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class ClientAutoTest {
	private static final Robot ROBOT;
	private static final ModContainer MOD_CONTAINER;

	public static void runTest() {
		Point quitButton = waitUntil("Minecraft title screen", imageOnScreen("quit_button.png"), Duration.ofMinutes(10));
		saveScreenshot("title_screen");
		clickPoint(quitButton);
	}

	private static Supplier<@Nullable Point> imageOnScreen(String... imageNames) {
		return () -> {
			BufferedImage screenshot = ROBOT.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

			for (String imageName : imageNames) {
				Point point = findOnScreen(screenshot, imageName);

				if (point != null) {
					return point;
				}
			}

			return null;
		};
	}

	private static Point findOnScreen(BufferedImage screenshot, String imagePath) {
		final Path path = MOD_CONTAINER.findPath("images/" + imagePath).orElseThrow(() -> new RuntimeException("Failed to find image"));
		final BufferedImage image = readImage(path);

		// Loop every pixel in the screenshot
		for (int x = 0; x < screenshot.getWidth() - image.getWidth(); x++) {
			for (int y = 0; y < screenshot.getHeight() - image.getHeight(); y++) {
				boolean found = true;

				// Trying to match the image with the screenshot
				for (int dx = 0; dx < image.getWidth(); dx++) {
					for (int dy = 0; dy < image.getHeight(); dy++) {
						if (screenshot.getRGB(x + dx, y + dy) != image.getRGB(dx, dy)) {
							found = false;
							break;
						}
					}

					if (!found) {
						break;
					}
				}

				if (found) {
					Log.info(LogCategory.TEST, "Found image at " + x + ", " + y);
					return new Point(x, y);
				}
			}
		}

		return null;
	}

	private static BufferedImage readImage(Path path) {
		try (InputStream stream = Files.newInputStream(path)) {
			return ImageIO.read(stream);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read image", e);
		}
	}

	private static void saveScreenshot(String name) {
		BufferedImage screenshot = ROBOT.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
		Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(name + ".png");

		try {
			Files.createDirectories(output.getParent());
			ImageIO.write(screenshot, "png", Files.newOutputStream(output));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to save screenshot", e);
		}
	}

	private static void clickPoint(Point point) {
		ROBOT.mouseMove(point.x, point.y);
		ROBOT.mousePress(InputEvent.BUTTON1_DOWN_MASK);
		ROBOT.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
	}

	private static <T> T waitUntil(String what, Supplier<@Nullable T> supplier, Duration timeout) {
		final LocalDateTime end = LocalDateTime.now().plus(timeout);

		while (true) {
			T value = supplier.get();

			if (value != null) {
				return value;
			}

			if (LocalDateTime.now().isAfter(end)) {
				throw new RuntimeException("Timed out waiting for " + what);
			}

			Log.info(LogCategory.TEST, "Waiting for " + what + "...");

			waitFor(Duration.ofSeconds(1));
		}
	}

	private static void waitFor(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	static {
		try {
			ROBOT = new Robot();
		} catch (AWTException e) {
			throw new RuntimeException(e);
		}

		MOD_CONTAINER = FabricLoader.getInstance().getModContainer("fabric-loader-auto-test")
				.orElseThrow(() -> new RuntimeException("Failed to find mod container"));
	}
}
