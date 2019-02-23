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

package net.fabricmc.loader;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.language.LanguageAdapter;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.fabricmc.loader.util.FileSystemUtil;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ModContainer implements net.fabricmc.loader.api.ModContainer {
	private static final Map<String, LanguageAdapter> adapterMap = new HashMap<>();

	private final LoaderModMetadata info;
	private final URL originUrl;
	private Path root;
	private LanguageAdapter adapter;

	public ModContainer(LoaderModMetadata info, URL originUrl) {
		this.info = info;
		this.originUrl = originUrl;
	}

	void instantiate() {
		this.adapter = createAdapter();

		try {
			Path holder = UrlUtil.asPath(originUrl).toAbsolutePath();
			if (Files.isDirectory(holder)) {
				root = holder.toAbsolutePath();
			} else /* JAR */ {
				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(holder, false);
				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + holder.getFileName() + " for NIO reading!");
				}

				root = delegate.get().getRootDirectories().iterator().next();

				// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
			}
		} catch (IOException | UrlConversionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ModMetadata getMetadata() {
		return info;
	}

	@Override
	public Path getRoot() {
		if (root == null) {
			throw new RuntimeException("Accessed mod root before primary loader!");
		}
		return root;
	}

	public LoaderModMetadata getInfo() {
		return info;
	}

	public URL getOriginUrl() {
		return originUrl;
	}

	public LanguageAdapter getAdapter() {
		return adapter;
	}

	private LanguageAdapter createAdapter() {
		return adapterMap.computeIfAbsent(info.getLanguageAdapter(), (adapter) -> {
			try {
				return (LanguageAdapter) FabricLauncherBase.getClass(adapter).getDeclaredConstructor().newInstance();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Unable to create language adapter %s for mod %s", adapter, info.getId()), e);
			}
		});
	}
}
