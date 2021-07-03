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

import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

public final class ModDependencyImpl implements ModDependency {
	private final Kind kind;
	private final String modId;
	private final List<String> matcherStringList;
	private final Collection<VersionPredicate> ranges;

	public ModDependencyImpl(Kind kind, String modId, List<String> matcherStringList) throws VersionParsingException {
		this.kind = kind;
		this.modId = modId;
		this.matcherStringList = matcherStringList;
		this.ranges = VersionPredicate.parse(this.matcherStringList);
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	@Override
	public String getModId() {
		return this.modId;
	}

	@Override
	public boolean matches(Version version) {
		for (VersionPredicate predicate : ranges) {
			if (predicate.test(version)) return true;
		}

		return false;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder("{");
		builder.append(kind.getKey());
		builder.append(' ');
		builder.append(this.modId);
		builder.append(" @ [");

		for (int i = 0; i < matcherStringList.size(); i++) {
			if (i > 0) {
				builder.append(" || ");
			}

			builder.append(matcherStringList.get(i));
		}

		builder.append("]}");
		return builder.toString();
	}

	@Override
	public Collection<VersionPredicate> getVersionRequirements() {
		return ranges;
	}
}
