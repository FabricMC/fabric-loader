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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateFinder.ModCandidateConsumer;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class ModDiscoverer {
	private final List<ModCandidateFinder> candidateFinders = new ArrayList<>();
	private final EnvType envType = FabricLoaderImpl.INSTANCE.getEnvironmentType();
	private final Map<Long, ModScanTask> jijDedupMap = new ConcurrentHashMap<>(); // avoids reading the same jar twice
	private final List<NestedModInitData> nestedModInitDatas = Collections.synchronizedList(new ArrayList<>()); // breaks potential cycles from deduplication

	public void addCandidateFinder(ModCandidateFinder f) {
		candidateFinders.add(f);
	}

	public Collection<ModCandidate> discoverMods(FabricLoaderImpl loader) throws ModResolutionException {
		long startTime = System.nanoTime();
		ForkJoinPool pool = new ForkJoinPool();
		List<Future<ModCandidate>> futures = new ArrayList<>();

		ModCandidateConsumer taskSubmitter = (path, requiresRemap) -> {
			futures.add(pool.submit(new ModScanTask(path, requiresRemap)));
		};

		for (ModCandidateFinder finder : candidateFinders) {
			finder.findCandidates(taskSubmitter);
		}

		List<ModCandidate> candidates = new ArrayList<>();

		// add builtin mods
		for (BuiltinMod mod : loader.getGameProvider().getBuiltinMods()) {
			candidates.add(ModCandidate.createBuiltin(mod));
		}

		// Add the current Java version
		candidates.add(ModCandidate.createBuiltin(new BuiltinMod(
				Paths.get(System.getProperty("java.home")),
				new BuiltinModMetadata.Builder("java", System.getProperty("java.specification.version").replaceFirst("^1\\.", ""))
				.setName(System.getProperty("java.vm.name"))
				.build())));

		ModResolutionException exception = null;

		try {
			pool.shutdown();
			// Comment out for debugging
			pool.awaitTermination(30, TimeUnit.SECONDS);

			for (Future<ModCandidate> future : futures) {
				if (!future.isDone()) {
					throw new ModResolutionException("Mod discovery took too long!");
				}

				try {
					ModCandidate candidate = future.get();
					if (candidate != null) candidates.add(candidate);
				} catch (ExecutionException e) {
					exception = ExceptionUtil.gatherExceptions(e, exception, exc -> new ModResolutionException("Mod discovery failed!", exc));
				}
			}

			for (NestedModInitData data : nestedModInitDatas) {
				for (Future<ModCandidate> future : data.futures) {
					if (!future.isDone()) {
						throw new ModResolutionException("Mod discovery took too long!");
					}

					try {
						data.target.add(future.get());
					} catch (ExecutionException e) {
						exception = ExceptionUtil.gatherExceptions(e, exception, exc -> new ModResolutionException("Mod discovery failed!", exc));
					}
				}
			}
		} catch (InterruptedException e) {
			throw new ModResolutionException("Mod discovery took too long!", e);
		}

		if (exception != null) {
			throw exception;
		}

		// initialize parent data

		Queue<ModCandidate> queue = new ArrayDeque<>(candidates);
		ModCandidate mod;

		while ((mod = queue.poll()) != null) {
			for (ModCandidate child : mod.getNestedMods()) {
				if (child.addParent(mod)) {
					queue.add(child);
				}
			}
		}

		long endTime = System.nanoTime();

		Log.debug(LogCategory.DISCOVERY, "Mod discovery time: %.1f ms", (endTime - startTime) * 1e-6);

		return candidates;
	}

	@SuppressWarnings("serial")
	final class ModScanTask extends RecursiveTask<ModCandidate> {
		private final Path path;
		private final String localPath;
		private final RewindableInputStream is;
		private final long hash;
		private final boolean requiresRemap;
		private final List<String> parentPaths;

		ModScanTask(Path path, boolean requiresRemap) {
			this(path, null, null, -1, requiresRemap, Collections.emptyList());
		}

		private ModScanTask(Path path, String localPath, RewindableInputStream is, long hash,
				boolean requiresRemap, List<String> parentPaths) {
			this.path = path;
			this.localPath = localPath != null ? localPath : path.toString();
			this.is = is;
			this.hash = hash;
			this.requiresRemap = requiresRemap;
			this.parentPaths = parentPaths;
		}

		@Override
		protected ModCandidate compute() {
			try {
				if (is != null) {
					return computeJarStream();
				} else if (Files.isDirectory(path)) {
					return computeDir();
				} else {
					return computeJarFile();
				}
			} catch (IOException | ParseMetadataException e) {
				throw ExceptionUtil.wrap(e);
			}
		}

		private ModCandidate computeDir() throws IOException, ParseMetadataException {
			Path modJson = path.resolve("fabric.mod.json");
			if (!Files.exists(modJson)) return null;

			LoaderModMetadata metadata;

			try (InputStream is = Files.newInputStream(modJson)) {
				metadata = ModMetadataParser.parseMetadata(is, localPath, parentPaths);
			}

			if (!metadata.loadsInEnvironment(envType)) {
				return null;
			}

			return ModCandidate.createPlain(path, metadata, requiresRemap, Collections.emptyList());
		}

		private ModCandidate computeJarFile() throws IOException, ParseMetadataException {
			try (ZipFile zf = new ZipFile(path.toFile())) {
				ZipEntry entry = zf.getEntry("fabric.mod.json");
				if (entry == null) return null;

				LoaderModMetadata metadata;

				try (InputStream is = zf.getInputStream(entry)) {
					metadata = ModMetadataParser.parseMetadata(is, localPath, parentPaths);
				}

				if (!metadata.loadsInEnvironment(envType)) {
					return null;
				}

				List<ModScanTask> nestedModTasks;

				if (metadata.getJars().isEmpty()) {
					nestedModTasks = Collections.emptyList();
				} else {
					Set<NestedJarEntry> nestedJarPaths = new HashSet<>(metadata.getJars());

					nestedModTasks = computeNestedMods(new ZipEntrySource() {
						@Override
						public ZipEntry getNextEntry() throws IOException {
							while (jarIt.hasNext()) {
								NestedJarEntry jar = jarIt.next();
								ZipEntry ret = zf.getEntry(jar.getFile());

								if (isValidNestedJarEntry(ret)) {
									currentEntry = ret;
									jarIt.remove();
									return ret;
								}
							}

							currentEntry = null;
							return null;
						}

						@Override
						public RewindableInputStream getInputStream() throws IOException {
							try (InputStream is = zf.getInputStream(currentEntry)) {
								return new RewindableInputStream(is);
							}
						}

						private final Iterator<NestedJarEntry> jarIt = nestedJarPaths.iterator();
						private ZipEntry currentEntry;
					});

					if (!nestedJarPaths.isEmpty() && FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
						Log.warn(LogCategory.METADATA, "Mod %s %s references missing nested jars: %s", metadata.getId(), metadata.getVersion(), nestedJarPaths);
					}
				}

				List<ModCandidate> nestedMods;

				if (nestedModTasks.isEmpty()) {
					nestedMods = Collections.emptyList();
				} else {
					nestedMods = new ArrayList<>();
					nestedModInitDatas.add(new NestedModInitData(nestedModTasks, nestedMods));
				}

				return ModCandidate.createPlain(path, metadata, requiresRemap, nestedMods);
			}
		}

		private ModCandidate computeJarStream() throws IOException, ParseMetadataException {
			LoaderModMetadata metadata = null;
			ZipEntry entry;

			try (ZipInputStream zis = new ZipInputStream(is)) {
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.getName().equals("fabric.mod.json")) {
						metadata = ModMetadataParser.parseMetadata(zis, localPath, parentPaths);
						break;
					}
				}
			}

			if (metadata == null) return null;

			if (!metadata.loadsInEnvironment(envType)) {
				return null;
			}

			Collection<NestedJarEntry> nestedJars = metadata.getJars();
			List<ModScanTask> nestedModTasks;

			if (nestedJars.isEmpty()) {
				nestedModTasks = Collections.emptyList();
			} else {
				Set<String> nestedJarPaths = new HashSet<>(nestedJars.size());

				for (NestedJarEntry nestedJar : nestedJars) {
					nestedJarPaths.add(nestedJar.getFile());
				}

				is.rewind();

				try (ZipInputStream zis = new ZipInputStream(is)) {
					nestedModTasks = computeNestedMods(new ZipEntrySource() {
						@Override
						public ZipEntry getNextEntry() throws IOException {
							if (nestedJarPaths.isEmpty()) return null;

							ZipEntry ret;

							while ((ret = zis.getNextEntry()) != null) {
								if (isValidNestedJarEntry(ret) && nestedJarPaths.remove(ret.getName())) {
									is = new RewindableInputStream(zis); // reads the entry, which completes the ZipEntry with any trailing header data
									return ret;
								}
							}

							return null;
						}

						@Override
						public RewindableInputStream getInputStream() throws IOException {
							return is;
						}

						private RewindableInputStream is;
					});
				}

				if (!nestedJarPaths.isEmpty() && FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
					Log.warn(LogCategory.METADATA, "Mod %s %s references missing nested jars: %s", metadata.getId(), metadata.getVersion(), nestedJarPaths);
				}
			}

			List<ModCandidate> nestedMods;

			if (nestedModTasks.isEmpty()) {
				nestedMods = Collections.emptyList();
			} else {
				nestedMods = new ArrayList<>();
				nestedModInitDatas.add(new NestedModInitData(nestedModTasks, nestedMods));
			}

			ModCandidate ret = ModCandidate.createNested(localPath, hash, metadata, requiresRemap, nestedMods);
			ret.setData(is.getBuffer());

			return ret;
		}

		private List<ModScanTask> computeNestedMods(ZipEntrySource entrySource) throws IOException {
			List<String> parentPaths = new ArrayList<>(this.parentPaths.size() + 1);
			parentPaths.addAll(this.parentPaths);
			parentPaths.add(localPath);

			List<ModScanTask> tasks = new ArrayList<>(5);
			ModScanTask localTask = null;
			ZipEntry entry;

			while ((entry = entrySource.getNextEntry()) != null) {
				long hash = ModCandidate.hash(entry);
				ModScanTask task = jijDedupMap.get(hash);

				if (task == null) {
					task = new ModScanTask(null, entry.getName(), entrySource.getInputStream(), hash, requiresRemap, parentPaths);
					ModScanTask prev = jijDedupMap.putIfAbsent(hash, task);

					if (prev != null) {
						task = prev;
					} else if (localTask == null) { // don't fork first task, leave it for this thread
						localTask = task;
					} else {
						task.fork();
					}
				}

				tasks.add(task);
			}

			if (tasks.isEmpty()) return Collections.emptyList();

			if (localTask != null) localTask.invoke();

			return tasks;
		}
	}

	private static boolean isValidNestedJarEntry(ZipEntry entry) {
		return entry != null && !entry.isDirectory() && entry.getName().endsWith(".jar");
	}

	private interface ZipEntrySource {
		ZipEntry getNextEntry() throws IOException;
		RewindableInputStream getInputStream() throws IOException;
	}

	private static final class RewindableInputStream extends InputStream {
		private final ByteBuffer buffer;
		private int pos;

		RewindableInputStream(InputStream parent) throws IOException { // no parent.close()
			buffer = readMod(parent);

			assert buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0;
		}

		public ByteBuffer getBuffer() {
			return buffer;
		}

		public void rewind() {
			pos = 0;
		}

		@Override
		public int read() throws IOException {
			if (pos >= buffer.limit()) {
				return -1;
			} else {
				return buffer.get(pos++) & 0xff;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int rem = buffer.limit() - pos;

			if (rem <= 0) {
				return -1;
			} else {
				len = Math.min(len, rem);
				System.arraycopy(buffer.array(), pos, b, off, len);
				pos += len;

				return len;
			}
		}
	}

	static ByteBuffer readMod(InputStream is) throws IOException {
		int available = is.available();
		boolean availableGood = available > 1;
		byte[] buffer = new byte[availableGood ? available : 30_000];
		int offset = 0;
		int len;

		while ((len = is.read(buffer, offset, buffer.length - offset)) >= 0) {
			offset += len;

			if (offset == buffer.length) {
				if (availableGood) {
					int val = is.read();
					if (val < 0) break;

					availableGood = false;
					buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, 30_000));
					buffer[offset++] = (byte) val;
				} else {
					buffer = Arrays.copyOf(buffer, buffer.length * 2);
				}
			}
		}

		return ByteBuffer.wrap(buffer, 0, offset);
	}

	private static class NestedModInitData {
		final List<? extends Future<ModCandidate>> futures;
		final List<ModCandidate> target;

		NestedModInitData(List<? extends Future<ModCandidate>> futures, List<ModCandidate> target) {
			this.futures = futures;
			this.target = target;
		}
	}
}
