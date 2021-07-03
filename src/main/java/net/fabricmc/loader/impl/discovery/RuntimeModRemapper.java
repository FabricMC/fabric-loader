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

package net.fabricmc.loader.impl.discovery;

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
import java.util.stream.Collectors;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class RuntimeModRemapper {
	public static void remap(Collection<ModCandidate> modCandidates, Path tmpDir, Path outputDir) {
		List<ModCandidate> modsToRemap = new ArrayList<>();

		for (ModCandidate mod : modCandidates) {
			if (mod.getRequiresRemap()) {
				modsToRemap.add(mod);
			}
		}

		if (modsToRemap.isEmpty()) return;

		FabricLauncher launcher = FabricLauncherBase.getLauncher();

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(launcher.getMappingConfiguration().getMappings(), "intermediary", launcher.getTargetNamespace()))
				.renameInvalidLocals(false)
				.build();

		try {
			remapper.readClassPathAsync(getRemapClasspath().toArray(new Path[0]));
		} catch (IOException e) {
			throw new RuntimeException("Failed to populate remap classpath", e);
		}

		Map<ModCandidate, RemapInfo> infoMap = new HashMap<>();

		try {
			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = new RemapInfo();
				infoMap.put(mod, info);

				InputTag tag = remapper.createInputTag();
				info.tag = tag;

				if (mod.hasPath()) {
					info.inputPath = mod.getPath();
				} else {
					info.inputPath = mod.copyToDir(tmpDir, true);
					info.inputIsTemp = true;
				}

				info.outputPath = outputDir.resolve(mod.getDefaultFileName());
				Files.deleteIfExists(info.outputPath);

				remapper.readInputsAsync(tag, info.inputPath);
			}

			//Done in a 2nd loop as we need to make sure all the inputs are present before remapping
			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(info.inputPath, false);

				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + info.inputPath.getFileName() + " for NIO reading!");
				}

				Path inputJar = delegate.get().getRootDirectories().iterator().next();
				outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);

				info.outputConsumerPath = outputConsumer;

				remapper.apply(outputConsumer, info.tag);
			}

			//Done in a 3rd loop as this can happen when the remapper is doing its thing.
			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);

				String accessWidener = mod.getMetadata().getAccessWidener();

				if (accessWidener != null) {
					info.accessWidenerPath = accessWidener;

					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.inputPath, false)) {
						FileSystem fs = jarFs.get();
						info.accessWidener = remapAccessWidener(Files.readAllBytes(fs.getPath(accessWidener)), remapper.getRemapper());
					}
				}
			}

			remapper.finish();

			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);

				info.outputConsumerPath.close();

				if (info.accessWidenerPath != null) {
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.outputPath, false)) {
						FileSystem fs = jarFs.get();

						Files.delete(fs.getPath(info.accessWidenerPath));
						Files.write(fs.getPath(info.accessWidenerPath), info.accessWidener);
					}
				}

				mod.setPath(info.outputPath);
			}
		} catch (Throwable t) {
			remapper.finish();

			for (RemapInfo info : infoMap.values()) {
				try {
					Files.deleteIfExists(info.outputPath);
				} catch (IOException e) {
					Log.warn(LogCategory.MOD_REMAP, "Error deleting failed output jar %s", info.outputPath, e);
				}
			}

			throw new RuntimeException("Failed to remap mods", t);
		} finally {
			for (RemapInfo info : infoMap.values()) {
				try {
					if (info.inputIsTemp) Files.deleteIfExists(info.inputPath);
				} catch (IOException e) {
					Log.warn(LogCategory.MOD_REMAP, "Error deleting temporary input jar %s", info.inputIsTemp, e);
				}
			}
		}
	}

	private static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
			AccessWidener accessWidener = new AccessWidener();
			AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);
			accessWidenerReader.read(bufferedReader, "intermediary");

			AccessWidenerRemapper accessWidenerRemapper = new AccessWidenerRemapper(accessWidener, remapper, "named");
			AccessWidener remapped = accessWidenerRemapper.remap();
			AccessWidenerWriter accessWidenerWriter = new AccessWidenerWriter(remapped);

			try (StringWriter writer = new StringWriter()) {
				accessWidenerWriter.write(writer);
				return writer.toString().getBytes(StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<Path> getRemapClasspath() throws IOException {
		String remapClasspathFile = System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE);

		if (remapClasspathFile == null) {
			throw new RuntimeException("No remapClasspathFile provided");
		}

		String content = new String(Files.readAllBytes(Paths.get(remapClasspathFile)), StandardCharsets.UTF_8);

		return Arrays.stream(content.split(File.pathSeparator))
				.map(Paths::get)
				.collect(Collectors.toList());
	}

	private static class RemapInfo {
		InputTag tag;
		Path inputPath;
		Path outputPath;
		boolean inputIsTemp;
		OutputConsumerPath outputConsumerPath;
		String accessWidenerPath;
		byte[] accessWidener;
	}
}
