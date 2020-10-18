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

package net.fabricmc.loader.discovery;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncher;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.transformer.accesswidener.AccessWidener;
import net.fabricmc.loader.transformer.accesswidener.AccessWidenerRemapper;
import net.fabricmc.loader.util.FileSystemUtil;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import net.fabricmc.loader.util.mappings.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RuntimeModRemapper {

	public static Collection<ModCandidate> remap(Collection<ModCandidate> modCandidates, FileSystem fileSystem) {
		List<ModCandidate> modsToRemap = modCandidates.stream()
				.filter(ModCandidate::requiresRemap)
				.collect(Collectors.toList());

		if (modsToRemap.isEmpty()) {
			return modCandidates;
		}

		FabricLauncher launcher = FabricLauncherBase.getLauncher();

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(launcher.getMappingConfiguration().getMappings(), "intermediary", launcher.getTargetNamespace()))
				.renameInvalidLocals(false)
				.build();

		try {
			remapper.readClassPathAsync(getRemapClasspath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to populate remap classpath", e);
		}

		List<ModCandidate> remappedMods = new ArrayList<>();
		Stream<ModCandidate> modsToSkip;

		try {
			Map<ModCandidate, RemapInfo> infoMap = new HashMap<>();

			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = new RemapInfo(remapper.createInputTag(), UrlUtil.asPath(mod.getOriginUrl()));
				infoMap.put(mod, info);

				remapper.readInputsAsync(info.tag, info.inputPath);
			}

			//Done in a 2nd loop as we need to make sure all the inputs are present before remapping
			for (RemapInfo info : infoMap.values()) {
				do {
					info.outputPath = fileSystem.getPath(UUID.randomUUID() + ".jar");
				} while (Files.exists(info.outputPath));
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(info.inputPath, false);

				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + info.inputPath.getFileName() + " for NIO reading!");
				}

				Path inputJar = delegate.get().getRootDirectories().iterator().next();
				outputConsumer.addNonClassFiles(inputJar);

				info.outputConsumerPath = outputConsumer;

				remapper.apply(outputConsumer, info.tag);
			}

			//Done in a 3rd loop as this can happen when the remapper is doing its thing.
			for (Entry<ModCandidate, RemapInfo> entry : infoMap.entrySet()) {
				ModCandidate mod = entry.getKey();

				String accessWidener = mod.getInfo().getAccessWidener();

				if (accessWidener != null) {
					RemapInfo info = entry.getValue();
					info.accessWidenerPath = accessWidener;

					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.inputPath, false)) {
						FileSystem fs = jarFs.get();
						info.accessWidener = remapAccessWidener(Files.readAllBytes(fs.getPath(accessWidener)), remapper.getRemapper());
					}
				}
			}

			remapper.finish();

			for (Entry<ModCandidate, RemapInfo> entry : infoMap.entrySet()) {
				ModCandidate mod = entry.getKey();
				RemapInfo info = entry.getValue();

				info.outputConsumerPath.close();

				if (info.accessWidenerPath != null) {
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.outputPath, false)) {
						FileSystem fs = jarFs.get();

						Files.delete(fs.getPath(info.accessWidenerPath));
						Files.write(fs.getPath(info.accessWidenerPath), info.accessWidener);
					}
				}

				remappedMods.add(new ModCandidate(mod.getInfo(), UrlUtil.asUrl(info.outputPath), 0, false));
			}

			modsToSkip = modCandidates.stream().filter(mod -> !infoMap.containsKey(mod));
		} catch (UrlConversionException | IOException e) {
			remapper.finish();
			throw new RuntimeException("Failed to remap mods", e);
		}

		return Stream.concat(remappedMods.stream(), modsToSkip)
				.collect(Collectors.toList());
	}

	private static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
			@SuppressWarnings("deprecation")
			AccessWidener accessWidener = new AccessWidener(FabricLoader.INSTANCE);
			accessWidener.read(bufferedReader, "intermediary");

			AccessWidenerRemapper accessWidenerRemapper = new AccessWidenerRemapper(accessWidener, remapper, "named");
			AccessWidener remapped = accessWidenerRemapper.remap();

			try (StringWriter writer = new StringWriter()) {
				remapped.write(writer);
				return writer.toString().getBytes(StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Path[] getRemapClasspath() throws IOException {
		String remapClasspathFile = System.getProperty("fabric.remapClasspathFile");

		if (remapClasspathFile == null) {
			throw new RuntimeException("No remapClasspathFile provided");
		}

		String content = new String(Files.readAllBytes(Paths.get(remapClasspathFile)), StandardCharsets.UTF_8);

		return Arrays.stream(content.split(File.pathSeparator))
				.map(Paths::get)
				.toArray(Path[]::new);
	}

	private static class RemapInfo {
		final InputTag tag;
		final Path inputPath;
		Path outputPath;
		OutputConsumerPath outputConsumerPath;
		String accessWidenerPath;
		byte[] accessWidener;

		public RemapInfo(InputTag tag, Path inputPath) {
			this.tag = tag;
			this.inputPath = inputPath;
		}
	}
}
