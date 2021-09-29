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
import java.lang.reflect.InvocationTargetException;
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
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.minecraft.patch.BrandingPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch;
import net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatchFML125;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogHandler;

public class MinecraftGameProvider implements GameProvider {
	private static final String[] RESTRICTED_CLASS_PREFIXES = { "net.minecraft.", "com.mojang." };

	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar, realmsJar;
	private McVersion versionData;
	private boolean shadedLog4j;
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

		return getLaunchDirectory(arguments).toPath();
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
	public boolean isEnabled() {
		return System.getProperty(SystemProperties.SKIP_MC_PROVIDER) == null;
	}

	@Override
	public boolean locateGame(FabricLauncher launcher, String[] args, ClassLoader loader) {
		this.envType = launcher.getEnvironmentType();
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

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;
		realmsJar = GameProviderHelper.getSource(loader, "realmsVersion").orElse(null);
		shadedLog4j = gameJar.equals(GameProviderHelper.getSource(loader, "org/apache/logging/log4j/LogManager.class").orElse(null));
		hasModLoader = GameProviderHelper.getSource(loader, "ModLoader.class").isPresent();

		if (!shadedLog4j) { // use Log4J log handler directly if it is not shaded into the game jar, otherwise delay it to initialize() after deobfuscation
			setupLog4jLogHandler(launcher);
		}

		// expose obfuscated jar locations for mods to more easily remap code from obfuscated to intermediary
		ObjectShare share = FabricLoaderImpl.INSTANCE.getObjectShare();
		share.put("fabric-loader:inputGameJar", gameJar);
		if (realmsJar != null) share.put("fabric-loader:inputRealmsJar", realmsJar);

		String version = arguments.remove(Arguments.GAME_VERSION);
		if (version == null) version = System.getProperty(SystemProperties.GAME_VERSION);
		versionData = McVersionLookup.getVersion(gameJar, entrypointClasses, version);

		processArgumentMap(arguments, envType);

		return true;
	}

	private static void processArgumentMap(Arguments argMap, EnvType envType) {
		switch (envType) {
		case CLIENT:
			if (!argMap.containsKey("accessToken")) {
				argMap.put("accessToken", "FabricMC");
			}

			if (!argMap.containsKey("version")) {
				argMap.put("version", "Fabric");
			}

			String versionType = "";

			if (argMap.containsKey("versionType") && !argMap.get("versionType").equalsIgnoreCase("release")) {
				versionType = argMap.get("versionType") + "/";
			}

			argMap.put("versionType", versionType + "Fabric");

			if (!argMap.containsKey("gameDir")) {
				argMap.put("gameDir", getLaunchDirectory(argMap).getAbsolutePath());
			}

			break;
		case SERVER:
			argMap.remove("version");
			argMap.remove("gameDir");
			argMap.remove("assetsDir");
			break;
		}
	}

	private static File getLaunchDirectory(Arguments argMap) {
		return new File(argMap.getOrDefault("gameDir", "."));
	}

	@Override
	public void initialize(FabricLauncher launcher) {
		List<Path> gameJars = new ArrayList<>(2);
		gameJars.add(gameJar);

		if (realmsJar != null) {
			gameJars.add(realmsJar);
		}

		if (isObfuscated()) {
			gameJars = GameProviderHelper.deobfuscate(gameJars,
					getGameId(), getNormalizedGameVersion(),
					getLaunchDirectory(),
					launcher);

			gameJar = gameJars.get(0);
			if (gameJars.size() > 1) realmsJar = gameJars.get(1);
		}

		if (shadedLog4j) {
			launcher.addToClassPath(gameJar);
			launcher.setClassRestrictions(RESTRICTED_CLASS_PREFIXES);
			setupLog4jLogHandler(launcher);
		}
	}

	private void setupLog4jLogHandler(FabricLauncher launcher) {
		try {
			final String logHandlerClsName = "net.fabricmc.loader.impl.game.minecraft.Log4jLogHandler";
			Class<?> logHandlerCls;

			if (shadedLog4j) {
				logHandlerCls = launcher.loadIntoTarget(logHandlerClsName);
			} else {
				logHandlerCls = Class.forName(logHandlerClsName);
			}

			Log.init((LogHandler) logHandlerCls.getConstructor().newInstance(), true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Arguments getArguments() {
		return arguments;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		if (arguments == null) return new String[0];
		if (!sanitize) return arguments.toArray();

		List<String> list = new ArrayList<>(Arrays.asList(arguments.toArray()));
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

		return list.toArray(new String[0]);
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return TRANSFORMER;
	}

	@Override
	public boolean canOpenErrorGui() {
		if (arguments == null || envType == EnvType.CLIENT) {
			return true;
		}

		List<String> extras = arguments.getExtraArgs();
		return !extras.contains("nogui") && !extras.contains("--nogui");
	}

	@Override
	public boolean hasAwtSupport() {
		// MC always sets -XstartOnFirstThread for LWJGL
		return !LoaderUtil.hasMacOs();
	}

	@Override
	public void unlockClassPath(FabricLauncher launcher) {
		if (shadedLog4j) {
			launcher.setClassRestrictions(new String[0]);
		} else {
			launcher.addToClassPath(gameJar);
		}

		if (realmsJar != null) launcher.addToClassPath(realmsJar);
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		if (envType == EnvType.CLIENT && targetClass.contains("Applet")) {
			targetClass = "net.fabricmc.loader.impl.game.minecraft.applet.AppletMain";
		}

		try {
			Class<?> c = loader.loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (InvocationTargetException e) {
			if (!onCrash(e.getCause(), "Minecraft has crashed!")) {
				throw new RuntimeException("Minecraft has crashed", e.getCause()); // Pass it on
			}
		} catch (ReflectiveOperationException e) {
			if (!onCrash(e, "Failed to start Minecraft!")) {
				throw new RuntimeException("Failed to start Minecraft", e);
			}
		}
	}

	@Override
	public boolean onCrash(Throwable exception, String context) {
		FabricGuiEntry.displayError(context, exception, false);
		return false; // Allow the crash to propagate
	}
}
