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
import java.util.function.Supplier;

import net.fabricmc.loader.api.MappingResolver;

public class LazyMappingResolver implements MappingResolver {
	private final Supplier<MappingResolver> delegateSupplier;
	private final String currentRuntimeNamespace;

	private MappingResolver delegate = null;

	LazyMappingResolver(Supplier<MappingResolver> delegateSupplier, String currentRuntimeNamespace) {
		this.delegateSupplier = delegateSupplier;
		this.currentRuntimeNamespace = currentRuntimeNamespace;
	}

	private MappingResolver getDelegate() {
		if (delegate == null) {
			delegate = delegateSupplier.get();
		}

		return delegate;
	}

	@Override
	public Collection<String> getNamespaces() {
		return getDelegate().getNamespaces();
	}

	@Override
	public String getCurrentRuntimeNamespace() {
		return currentRuntimeNamespace;
	}

	@Override
	public String mapClassName(String namespace, String className) {
		if (namespace.equals(currentRuntimeNamespace)) {
			// Skip loading the mappings if the namespace is the same as the current runtime namespace
			return className;
		}

		return getDelegate().mapClassName(namespace, className);
	}

	@Override
	public String unmapClassName(String targetNamespace, String className) {
		return getDelegate().unmapClassName(targetNamespace, className);
	}

	@Override
	public String mapFieldName(String namespace, String owner, String name, String descriptor) {
		if (namespace.equals(currentRuntimeNamespace)) {
			// Skip loading the mappings if the namespace is the same as the current runtime namespace
			return name;
		}

		return getDelegate().mapFieldName(namespace, owner, name, descriptor);
	}

	@Override
	public String mapMethodName(String namespace, String owner, String name, String descriptor) {
		if (namespace.equals(currentRuntimeNamespace)) {
			// Skip loading the mappings if the namespace is the same as the current runtime namespace
			return name;
		}

		return getDelegate().mapMethodName(namespace, owner, name, descriptor);
	}
}
