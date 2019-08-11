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

package net.fabricmc.loader.game;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchBranding;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchFML125;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchHook;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.loader.util.FileSystemUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MinecraftGameProvider implements GameProvider {
	static class VersionData {
		public String id;
		public String name;
		public String release_target;
		public int world_version;
		public int protocol_version;
		public int pack_version;
		public String build_time;
		public boolean stable;
	}

	private static final Gson GSON = new Gson();

	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar, realmsJar;
	private VersionData versionData;
	private boolean hasModLoader = false;
	private EntrypointTransformer entrypointTransformer = new EntrypointTransformer(it -> Arrays.asList(
		new EntrypointPatchHook(it),
		new EntrypointPatchBranding(it),
		new EntrypointPatchFML125(it)
	));

	@Override
	public String getGameId() {
		if (versionData != null) {
			String id = versionData.id;
			if (id == null) {
				id = versionData.name;
			}

			if (id != null) {
				return "minecraft-" + id.replaceAll("[^a-zA-Z0-9.]+", "-");
			}
		}

		String filename = gameJar.getFileName().toString();
		if (filename.lastIndexOf('.') >= 0) {
			filename = filename.substring(0, filename.lastIndexOf('.'));
		}

		return "minecraft-" + filename;
	}

	@Override
	public String getGameName() {
		if (versionData != null && versionData.name != null) {
			return "Minecraft " + versionData.name;
		}
		
		return "Minecraft";
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
	public boolean locateGame(EnvType envType, ClassLoader loader) {
		this.envType = envType;
		List<String> entrypointClasses = (envType == EnvType.CLIENT
			   ? Lists.newArrayList("net.minecraft.client.main.Main", "net.minecraft.client.MinecraftApplet", "com.mojang.minecraft.MinecraftApplet")
			   : Lists.newArrayList("net.minecraft.server.MinecraftServer", "com.mojang.minecraft.server.MinecraftServer"));

		Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);
		if (!entrypointResult.isPresent()) {
			return false;
		}

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;
		realmsJar = GameProviderHelper.getSource(loader, "realmsVersion").orElse(null);
		hasModLoader = GameProviderHelper.getSource(loader, "ModLoader.class").isPresent();

		try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
			Path versionJson = jarFs.get().getPath("version.json");
			if (Files.exists(versionJson)) {
				versionData = GSON.fromJson(new String(Files.readAllBytes(versionJson), StandardCharsets.UTF_8), VersionData.class);
			}
		} catch (IOException e) {
			// TODO: migrate to Logger
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void acceptArguments(String... argStrs) {
		this.arguments = new Arguments();
		arguments.parse(argStrs);

		FabricLauncherBase.processArgumentMap(arguments, envType);
	}

	@Override
	public EntrypointTransformer getEntrypointTransformer() {
		return entrypointTransformer;
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		if (envType == EnvType.CLIENT && targetClass.contains("Applet")) {
			targetClass = "net.fabricmc.loader.entrypoint.applet.AppletMain";
		}

		try {
			Class<?> c = ((ClassLoader) loader).loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
