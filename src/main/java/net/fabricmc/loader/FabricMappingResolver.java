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

import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.mappings.*;

import java.util.*;
import java.util.function.Supplier;

class FabricMappingResolver implements MappingResolver {
	private final Supplier<Mappings> mappingsSupplier;
	private final Set<String> namespaces;
	private final Map<String, NamespaceData> namespaceDataMap = new HashMap<>();
	private final String targetNamespace;

	private static class NamespaceData {
		private final Map<String, String> classNames = new HashMap<>();
		private final Map<String, String> classNamesInverse = new HashMap<>();
		private final Map<EntryTriple, String> fieldNames = new HashMap<>();
		private final Map<EntryTriple, String> methodNames = new HashMap<>();
	}

	FabricMappingResolver(Supplier<Mappings> mappingsSupplier, String targetNamespace) {
		this.mappingsSupplier = mappingsSupplier;
		this.targetNamespace = targetNamespace;
		namespaces = Collections.unmodifiableSet(new HashSet<>(mappingsSupplier.get().getNamespaces()));
	}

	protected final NamespaceData getNamespaceData(String namespace) {
		return namespaceDataMap.computeIfAbsent(namespace, (ns) -> {
			if (!namespaces.contains(namespace)) {
				throw new IllegalArgumentException("Unknown namespace: " + namespace);
			}

			NamespaceData data = new NamespaceData();
			Mappings mappings = mappingsSupplier.get();

			for (ClassEntry classEntry : mappings.getClassEntries()) {
				data.classNames.put(classEntry.get(ns), classEntry.get(targetNamespace));
				data.classNamesInverse.put(classEntry.get(targetNamespace), classEntry.get(ns));
			}

			for (FieldEntry fieldEntry : mappings.getFieldEntries()) {
				data.fieldNames.put(fieldEntry.get(ns), fieldEntry.get(targetNamespace).getName());
			}

			for (MethodEntry methodEntry : mappings.getMethodEntries()) {
				data.methodNames.put(methodEntry.get(ns), methodEntry.get(targetNamespace).getName());
			}

			return data;
		});
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
}
