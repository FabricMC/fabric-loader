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

package net.fabricmc.loader.impl.metadata;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.metadata.ModOrigin;

public final class ModOriginImpl implements ModOrigin {
	private final Kind kind;
	private List<Path> paths;
	private String parentModId;
	private String parentSubLocation;

	public ModOriginImpl() {
		this.kind = Kind.UNKNOWN;
	}

	public ModOriginImpl(List<Path> paths) {
		this.kind = Kind.PATH;
		this.paths = paths;
	}

	public ModOriginImpl(String parentModId, String parentSubLocation) {
		this.kind = Kind.NESTED;
		this.parentModId = parentModId;
		this.parentSubLocation = parentSubLocation;
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	@Override
	public List<Path> getPaths() {
		if (kind != Kind.PATH) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have paths");

		return paths;
	}

	@Override
	public String getParentModId() {
		if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have a parent mod");

		return parentModId;
	}

	@Override
	public String getParentSubLocation() {
		if (kind != Kind.NESTED) throw new UnsupportedOperationException("kind "+kind.name()+" doesn't have a parent sub-location");

		return parentSubLocation;
	}

	@Override
	public String toString() {
		switch (getKind()) {
		case PATH:
			return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
		case NESTED:
			return String.format("%s:%s", parentModId, parentSubLocation);
		default:
			return "unknown";
		}
	}
}
