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

package net.fabricmc.loader.impl;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.UrlConversionException;
import net.fabricmc.loader.impl.util.UrlUtil;

@SuppressWarnings("deprecation")
public class ModContainerImpl extends net.fabricmc.loader.ModContainer {
	private final LoaderModMetadata info;
	private final URL originUrl;
	private volatile Path root;

	public ModContainerImpl(LoaderModMetadata info, URL originUrl) {
		this.info = info;
		this.originUrl = originUrl;
	}

	@Override
	public ModMetadata getMetadata() {
		return info;
	}

	@Override
	public Path getRootPath() {
		Path ret = root;

		if (ret == null) {
			root = ret = obtainRootPath(); // obtainRootPath is thread safe, but we need to avoid plain or repeated reads to root
		}

		return ret;
	}

	private Path obtainRootPath() {
		try {
			Path holder = UrlUtil.asPath(originUrl).toAbsolutePath();

			if (Files.isDirectory(holder)) {
				return holder;
			} else /* JAR */ {
				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(holder, false);

				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + holder.getFileName() + " for NIO reading!");
				}

				return delegate.get().getRootDirectories().iterator().next();

				// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
			}
		} catch (IOException | UrlConversionException e) {
			throw new RuntimeException("Failed to find root directory for mod '" + info.getId() + "'!", e);
		}
	}

	@Override
	public LoaderModMetadata getInfo() {
		return info;
	}

	@Override
	public URL getOriginUrl() {
		return originUrl;
	}
}
