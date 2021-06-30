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

package net.fabricmc.loader.impl.game.minecraft;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.minecraft.patch.BrandingPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatchFML125;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;

public class MinecraftGameProvider implements GameProvider {
	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar, realmsJar;
	private McVersion versionData;
	private boolean hasModLoader = false;

	public static final GameTransformer TRANSFORMER = new GameTransformer(it -> Arrays.asList(
			new EntrypointPatch(it),
			new BrandingPatch(it),
			new EntrypointPatchFML125(it)
			));

	@Override
	public String getGameId() {
		return "minecraft";
	}

	@Override
	public String getGameName() {
		return "Minecraft";
	}

	@Override
	public String getRawGameVersion() {
		return versionData.getRaw();
	}

	@Override
	public String getNormalizedGameVersion() {
		return versionData.getNormalized();
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
				.setName(getGameName());

		if (versionData.getClassVersion().isPresent()) {
			int version = versionData.getClassVersion().getAsInt() - 44;

			try {
				metadata.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "java", Collections.singletonList(String.format(">=%d", version))));
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		return Collections.singletonList(new BuiltinMod(gameJar, metadata.build()));
	}

	public Path getGameJar() {
		return gameJar;
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return new File(".").toPath();
		}

		return FabricLauncherBase.getLaunchDirectory(arguments).toPath();
	}

	@Override
	public boolean isObfuscated() {
		return true; // generally yes...
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return hasModLoader;
	}

	@Override
	public List<Path> getGameContextJars() {
		List<Path> list = new ArrayList<>();
		list.add(gameJar);

		if (realmsJar != null) {
			list.add(realmsJar);
		}

		return list;
	}

	@Override
	public boolean locateGame(EnvType envType, String[] args, ClassLoader loader) {
		this.envType = envType;
		this.arguments = new Arguments();
		arguments.parse(args);

		List<String> entrypointClasses;

		if (envType == EnvType.CLIENT) {
			entrypointClasses = Arrays.asList("net.minecraft.client.main.Main", "net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet");
		} else {
			entrypointClasses = Arrays.asList("net.minecraft.server.Main", "net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer");
		}

		Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);

		if (!entrypointResult.isPresent()) {
			return false;
		}

		Log.init(new Log4jLogHandler(), true);

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;
		realmsJar = GameProviderHelper.getSource(loader, "realmsVersion").orElse(null);
		hasModLoader = GameProviderHelper.getSource(loader, "ModLoader.class").isPresent();

		String version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION);
		versionData = McVersionLookup.getVersion(gameJar, entrypointClasses, version);

		FabricLauncherBase.processArgumentMap(arguments, envType);

		return true;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments != null) {
			List<String> list = new ArrayList<>(Arrays.asList(arguments.toArray()));

			if (sanitize) {
				int remove = 0;
				Iterator<String> iterator = list.iterator();

				while (iterator.hasNext()) {
					String next = iterator.next();

					if ("--accessToken".equals(next)) {
						remove = 2;
					}

					if (remove > 0) {
						iterator.remove();
						remove--;
					}
				}
			}

			return list.toArray(new String[0]);
		}

		return new String[0];
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public boolean canOpenErrorGui() {
		// Disabled on macs due to -XstartOnFirstThread being incompatible with awt but required for lwjgl
		if (System.getProperty("os.name").equals("Mac OS X")) {
			return false;
		}

		if (arguments == null || envType == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		if (envType == EnvType.CLIENT && targetClass.contains("Applet")) {
			targetClass = "net.fabricmc.loader.impl.game.minecraft.entrypoint.applet.AppletMain";
		}

		try {
			Class<?> c = loader.loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
