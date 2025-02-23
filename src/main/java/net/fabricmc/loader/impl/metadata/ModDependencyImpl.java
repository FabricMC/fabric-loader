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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.discovery.DomainObject;

public final class ModDependencyImpl implements ModDependency {
	private final String modId;
	private final List<String> matcherStringList;
	private Collection<VersionPredicate> ranges;
	private final ModEnvironment activationEnv;
	private final List<ModDependency> activationMatchers;
	private boolean dependencyConditionsResolved;
	private boolean isActive;
	private Kind kind;

	public ModDependencyImpl(Kind kind, String modId, List<String> matcherStringList) throws VersionParsingException {
		this(kind, modId, matcherStringList, ModEnvironment.UNIVERSAL, new ArrayList<>());
	}

	public ModDependencyImpl(Kind kind, String modId, List<String> matcherStringList, ModEnvironment activationEnv, List<ModDependency> activationMatchers) throws VersionParsingException {
		this.kind = kind;
		this.modId = modId;
		this.matcherStringList = matcherStringList;
		this.ranges = VersionPredicate.parse(this.matcherStringList);
		this.activationEnv = activationEnv;
		this.activationMatchers = activationMatchers;
		this.dependencyConditionsResolved = false;
		this.isActive = false;
	}

	@Override
	public Kind getKind() {
		return kind;
	}

	public void setKind(Kind kind) {
		this.kind = kind;
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
	public boolean isActive() {
		if (!dependencyConditionsResolved) throw new IllegalStateException("Dependency conditions not resolved");
		return isActive;
	}

	@Override
	public void resolveDependencyConditions(Map<String, List<DomainObject.Mod>> allMods, EnvType envType) throws VersionParsingException {
		dependencyConditionsResolved = true;

		if (kind == Kind.CONDITION) {
			isActive = activationEnv.matches(envType);
			if (!isActive) return;

			for (ModDependency matcher : activationMatchers) {
				if (matcher.getKind() != Kind.DEPENDS) throw new IllegalStateException("Conditional dependency's matcher must be a DEPENDS dependency");
				matcher.resolveDependencyConditions(allMods, envType);

				if (!matcher.isActive()) {
					continue;
				}

				boolean found = false;

				if (allMods.containsKey(matcher.getModId())) {
					for (DomainObject.Mod mod : allMods.get(matcher.getModId())) {
						if (matcher.matches(mod.getVersion())) {
							found = true;
							break;
						}
					}
				}

				if (!found) {
					isActive = false;
					return;
				}
			}

			return;
		}

		for (ModDependency matcher : activationMatchers) {
			if (matcher.getKind() != Kind.CONDITION) throw new IllegalStateException("Non-conditional dependency's matcher must be a CONDITION dependency");
			matcher.resolveDependencyConditions(allMods, envType);

			if (matcher.isActive()) {
				matcherStringList.addAll(matcher.getMatcherStrings());
			}
		}

		ranges = VersionPredicate.parse(matcherStringList);
		isActive = !ranges.isEmpty();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ModDependency)) return false;

		ModDependency o = (ModDependency) obj;

		return kind == o.getKind()
				&& modId.equals(o.getModId())
				&& ranges.equals(o.getVersionRequirements());
	}

	@Override
	public int hashCode() {
		return (kind.ordinal() * 31 + modId.hashCode()) * 257 + ranges.hashCode();
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder("{");

		if (kind == Kind.CONDITION) {
			builder.append("conditional ");

			if (activationEnv != ModEnvironment.UNIVERSAL) {
				builder.append(activationEnv.name());
				builder.append(' ');
			}

			builder.append("[");

			for (int i = 0; i < matcherStringList.size(); i++) {
				if (i > 0) {
					builder.append(" || ");
				}

				builder.append(matcherStringList.get(i));
			}

			builder.append("] ");

			if (!activationMatchers.isEmpty()) {
				builder.append("[");
			}

			for (ModDependency matcher : activationMatchers) {
				if (activationMatchers.indexOf(matcher) > 0) {
					builder.append(" && ");
				}

				builder.append(matcher.toString());
			}

			if (!activationMatchers.isEmpty()) {
				builder.append("] ");
			}
		} else {
			int i;

			builder.append(kind.getKey());
			builder.append(' ');
			builder.append(this.modId);
			builder.append(" @ [");

			for (i = 0; i < matcherStringList.size(); i++) {
				if (i > 0) {
					builder.append(" || ");
				}

				builder.append(matcherStringList.get(i));
			}

			if (!dependencyConditionsResolved) {
				for (ModDependency matcher : activationMatchers) {
					if (i > 0) {
						builder.append(" || ");
					}

					builder.append(matcher.toString());
				}
			}

			builder.append("]");
		}

		builder.append("}");
		return builder.toString();
	}

	@Override
	public Collection<VersionPredicate> getVersionRequirements() {
		return ranges;
	}

	@Override
	public List<VersionInterval> getVersionIntervals() {
		List<VersionInterval> ret = Collections.emptyList();

		for (VersionPredicate predicate : ranges) {
			ret = VersionInterval.or(ret, predicate.getInterval());
		}

		return ret;
	}

	@Override
	public List<String> getMatcherStrings() {
		return matcherStringList;
	}
}
