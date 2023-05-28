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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.mappingio.tree.MappingTree;

class MappingResolverImpl implements MappingResolver {
	private final MappingTree mappings;
	private final Set<String> namespaces;
	private final Map<String, NamespaceData> namespaceDataMap = new HashMap<>();
	private final String targetNamespace;

	private static class NamespaceData {
		private final Map<String, String> classNames = new HashMap<>();
		private final Map<String, String> classNamesInverse = new HashMap<>();
		private final Map<EntryTriple, String> fieldNames = new HashMap<>();
		private final Map<EntryTriple, String> methodNames = new HashMap<>();
	}

	MappingResolverImpl(MappingTree mappings, String targetNamespace) {
		this.mappings = mappings;
		this.targetNamespace = targetNamespace;

		HashSet<String> nsSet = new HashSet<>(mappings.getDstNamespaces());
		nsSet.add(mappings.getSrcNamespace());
		namespaces = Collections.unmodifiableSet(nsSet);
	}

	protected final NamespaceData getNamespaceData(String namespace) {
		return namespaceDataMap.computeIfAbsent(namespace, (fromNamespace) -> {
			if (!namespaces.contains(namespace)) {
				throw new IllegalArgumentException("Unknown namespace: " + namespace);
			}

			NamespaceData data = new NamespaceData();
			Map<String, String> classNameMap = new HashMap<>();

			for (MappingTree.ClassMapping classEntry : mappings.getClasses()) {
				String fromClass = mapClassName(classNameMap, classEntry.getName(fromNamespace));
				String toClass = mapClassName(classNameMap, classEntry.getName(targetNamespace));

				data.classNames.put(fromClass, toClass);
				data.classNamesInverse.put(toClass, fromClass);

				String mappedClassName = mapClassName(classNameMap, fromClass);

				recordMember(fromNamespace, classEntry.getFields(), data.fieldNames, mappedClassName);
				recordMember(fromNamespace, classEntry.getMethods(), data.methodNames, mappedClassName);
			}

			return data;
		});
	}

	private static String replaceSlashesWithDots(String cname) {
		return cname.replace('/', '.');
	}

	private String mapClassName(Map<String, String> classNameMap, String s) {
		return classNameMap.computeIfAbsent(s, MappingResolverImpl::replaceSlashesWithDots);
	}

	private <T extends MappingTree.MemberMapping> void recordMember(String fromNamespace, Collection<T> descriptoredList, Map<EntryTriple, String> putInto, String fromClass) {
		for (T descriptored : descriptoredList) {
			EntryTriple fromEntry = new EntryTriple(fromClass, descriptored.getName(fromNamespace), descriptored.getDesc(fromNamespace));
			putInto.put(fromEntry, descriptored.getName(targetNamespace));
		}
	}

	@Override
	public Collection<String> getNamespaces() {
		return namespaces;
	}

	@Override
	public String getCurrentRuntimeNamespace() {
		return targetNamespace;
	}

	@Override
	public String mapClassName(String namespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return getNamespaceData(namespace).classNames.getOrDefault(className, className);
	}

	@Override
	public String unmapClassName(String namespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return getNamespaceData(namespace).classNamesInverse.getOrDefault(className, className);
	}

	@Override
	public String mapFieldName(String namespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}

		return getNamespaceData(namespace).fieldNames.getOrDefault(new EntryTriple(owner, name, descriptor), name);
	}

	@Override
	public String mapMethodName(String namespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}

		return getNamespaceData(namespace).methodNames.getOrDefault(new EntryTriple(owner, name, descriptor), name);
	}

	private static final class EntryTriple {
		final String owner;
		final String name;
		final String descriptor;

		EntryTriple(String owner, String name, String descriptor) {
			this.owner = owner;
			this.name = name;
			this.descriptor = descriptor;
		}

		@Override
		public String toString() {
			return "EntryTriple{owner=" + owner + ",name=" + name + ",desc=" + descriptor + "}";
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof EntryTriple)) {
				return false;
			} else if (o == this) {
				return true;
			} else {
				EntryTriple other = (EntryTriple) o;

				return other.owner.equals(owner) && other.name.equals(name) && other.descriptor.equals(descriptor);
			}
		}

		@Override
		public int hashCode() {
			return owner.hashCode() * 37 + name.hashCode() * 19 + descriptor.hashCode();
		}
	}
}
