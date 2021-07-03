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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

public final class ModCandidate implements DomainObject.Mod {
	private Path path;
	private final String localPath;
	private final long hash;
	private final LoaderModMetadata metadata;
	private final boolean requiresRemap;
	private final Collection<ModCandidate> nestedMods;
	private final Collection<ModCandidate> parentMods;
	private int minNestLevel;
	private SoftReference<ByteBuffer> dataRef;

	static ModCandidate createBuiltin(BuiltinMod mod) {
		return new ModCandidate(mod.path, null, -1, new BuiltinMetadataWrapper(mod.metadata), false, Collections.emptyList());
	}

	static ModCandidate createPlain(Path path, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidate> nestedMods) {
		return new ModCandidate(path, null, -1, metadata, requiresRemap, nestedMods);
	}

	static ModCandidate createNested(String localPath, long hash, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidate> nestedMods) {
		return new ModCandidate(null, localPath, hash, metadata, requiresRemap, nestedMods);
	}

	static long hash(ZipEntry entry) {
		if (entry.getSize() < 0 || entry.getCrc() < 0) throw new IllegalArgumentException("uninitialized entry: "+entry);

		return entry.getCrc() << 32 | entry.getSize();
	}

	private static long getSize(long hash) {
		return hash & 0xffffffffL;
	}

	private ModCandidate(Path path, String localPath, long hash, LoaderModMetadata metadata, boolean requiresRemap, Collection<ModCandidate> nestedMods) {
		this.path = path;
		this.localPath = localPath;
		this.metadata = metadata;
		this.hash = hash;
		this.requiresRemap = requiresRemap;
		this.nestedMods = nestedMods;
		this.parentMods = path == null ? new ArrayList<>() : Collections.emptyList();
		this.minNestLevel = path != null ? 0 : Integer.MAX_VALUE;
	}

	public boolean hasPath() {
		return path != null;
	}

	public Path getPath() {
		if (path == null) throw new IllegalStateException("no path set");

		return path;
	}

	public void setPath(Path path) {
		if (path == null) throw new NullPointerException("null path");

		this.path = path;
		clearCachedData();
	}

	String getLocalPath() {
		return localPath != null ? localPath : path.toString();
	}

	public LoaderModMetadata getMetadata() {
		return metadata;
	}

	@Override
	public String getId() {
		return metadata.getId();
	}

	@Override
	public Version getVersion() {
		return metadata.getVersion();
	}

	public Collection<String> getProvides() {
		return metadata.getProvides();
	}

	public boolean isBuiltin() {
		return metadata.getType().equals("builtin");
	}

	public ModLoadCondition getLoadCondition() {
		return minNestLevel == 0 ? ModLoadCondition.ALWAYS : ModLoadCondition.IF_POSSIBLE;
	}

	public Collection<ModDependency> getDependencies() {
		return metadata.getDependencies();
	}

	public boolean getRequiresRemap() {
		return requiresRemap;
	}

	public Collection<ModCandidate> getNestedMods() {
		return nestedMods;
	}

	public Collection<ModCandidate> getParentMods() {
		return parentMods;
	}

	boolean addParent(ModCandidate parent) {
		if (minNestLevel == 0) return false;
		if (parentMods.contains(parent)) return false;

		parentMods.add(parent);
		updateMinNestLevel(parent);

		return true;
	}

	public int getMinNestLevel() {
		return minNestLevel;
	}

	boolean resetMinNestLevel() {
		if (minNestLevel > 0) {
			minNestLevel = Integer.MAX_VALUE;
			return true;
		} else {
			return false;
		}
	}

	boolean updateMinNestLevel(ModCandidate parent) {
		if (minNestLevel <= parent.minNestLevel) return false;

		this.minNestLevel = parent.minNestLevel + 1;

		return true;
	}

	public boolean isRoot() {
		return minNestLevel == 0;
	}

	void setData(ByteBuffer data) {
		this.dataRef = new SoftReference<>(data);
	}

	void clearCachedData() {
		this.dataRef = null;
	}

	public Path copyToDir(Path outputDir, boolean temp) throws IOException {
		Files.createDirectories(outputDir);
		Path ret = null;

		try {
			if (temp) {
				ret = Files.createTempFile(outputDir, getId(), ".jar");
			} else {
				ret = outputDir.resolve(getDefaultFileName());

				if (Files.exists(ret)) {
					if (Files.size(ret) == getSize(hash)) {
						return ret;
					} else {
						Files.deleteIfExists(ret);
					}
				}
			}

			copyToFile(ret);
		} catch (Throwable t) {
			if (ret != null) Files.deleteIfExists(ret);

			throw t;
		}

		return ret;
	}

	String getDefaultFileName() {
		String ret = String.format("%s-%s-%s.jar",
				getId(),
				FILE_NAME_SANITIZING_PATTERN.matcher(getVersion().getFriendlyString()).replaceAll("_"),
				Long.toHexString(mixHash(hash)));

		if (ret.length() > 64) {
			ret = ret.substring(0, 32).concat(ret.substring(ret.length() - 32));
		}

		return ret;
	}

	private static long mixHash(long hash) {
		hash ^= (hash >>> 33);
		hash *= 0xff51afd7ed558ccdL;
		hash ^= (hash >>> 33);
		hash *= 0xc4ceb9fe1a85ec53L;
		hash ^= (hash >>> 33);

		return hash;
	}

	private static final Pattern FILE_NAME_SANITIZING_PATTERN = Pattern.compile("[^\\w\\.\\-\\+]+");

	private void copyToFile(Path out) throws IOException {
		SoftReference<ByteBuffer> dataRef = this.dataRef;

		if (dataRef != null) {
			ByteBuffer data = dataRef.get();

			if (data != null) {
				Files.copy(new ByteArrayInputStream(data.array(), data.arrayOffset() + data.position(), data.arrayOffset() + data.limit()), out);
				return;
			}
		}

		if (path != null) {
			Files.copy(path, out);
			return;
		}

		ModCandidate parent = getBestSourcingParent();

		if (parent.path != null) {
			try (ZipFile zf = new ZipFile(parent.path.toFile())) {
				ZipEntry entry = zf.getEntry(localPath);
				if (entry == null) throw new IOException(String.format("can't find nested mod %s in its parent mod %s", this, parent));

				Files.copy(zf.getInputStream(entry), out);
			}
		} else {
			ByteBuffer data = parent.getData();

			try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data.array(), data.arrayOffset() + data.position(), data.arrayOffset() + data.limit()))) {
				ZipEntry entry = null;

				while ((entry = zis.getNextEntry()) != null) {
					if (entry.getName().equals(localPath)) {
						Files.copy(zis, out);
						return;
					}
				}
			}

			throw new IOException(String.format("can't find nested mod %s in its parent mod %s", this, parent));
		}
	}

	private ByteBuffer getData() throws IOException {
		SoftReference<ByteBuffer> dataRef = this.dataRef;

		if (dataRef != null) {
			ByteBuffer ret = dataRef.get();
			if (ret != null) return ret;
		}

		ByteBuffer ret;

		if (path != null) {
			ret = ByteBuffer.wrap(Files.readAllBytes(path));
		} else {
			ModCandidate parent = getBestSourcingParent();

			if (parent.path != null) {
				try (ZipFile zf = new ZipFile(parent.path.toFile())) {
					ZipEntry entry = zf.getEntry(localPath);
					if (entry == null) throw new IOException(String.format("can't find nested mod %s in its parent mod %s", this, parent));

					ret = ModDiscoverer.readMod(zf.getInputStream(entry));
				}
			} else {
				ByteBuffer data = parent.getData();
				ret = null;

				try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data.array(), data.arrayOffset() + data.position(), data.arrayOffset() + data.limit()))) {
					ZipEntry entry = null;

					while ((entry = zis.getNextEntry()) != null) {
						if (entry.getName().equals(localPath)) {
							ret = ModDiscoverer.readMod(zis);
							break;
						}
					}
				}

				if (ret == null) throw new IOException(String.format("can't find nested mod %s in its parent mods %s", this, parent));
			}
		}

		this.dataRef = new SoftReference<>(ret);

		return ret;
	}

	private ModCandidate getBestSourcingParent() {
		if (parentMods.isEmpty()) return null;

		ModCandidate ret = null;

		for (ModCandidate parent : parentMods) {
			if (parent.minNestLevel >= minNestLevel) continue;

			if (parent.path != null
					|| parent.dataRef != null && parent.dataRef.get() != null) {
				return parent;
			}

			if (ret == null) ret = parent;
		}

		if (ret == null) throw new IllegalStateException("invalid nesting?");

		return ret;
	}

	@Override
	public String toString() {
		return String.format("%s %s", getId(), getVersion());
	}
}
