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

package net.fabricmc.loader.api;

import java.util.Collection;

/**
 * Helper class for performing mapping resolution.
 *
 * Note: The target namespace (the one being mapped to) for mapping (or the
 * source one for unmapping) is always implied to be the one Loader is
 * currently operating in.
 *
 * @since 0.4.1
 */
public interface MappingResolver {
	/**
	 * Get the list of all available mapping namespaces in the loaded instance.
	 * @return The list of all available namespaces.
	 */
	Collection<String> getNamespaces();

	/**
	 * Get the current namespace being used at runtime.
	 * @return The current namespace being used at runtime.
	 */
	String getCurrentRuntimeNamespace();

	/**
	 * Map a class name to the mapping currently used at runtime.
	 *
	 * @param namespace The namespace of the provided class name.
	 * @param className The provided class name, in dot-format ("mypackage.MyClass$Inner").
	 * @return The mapped class name, or className if such a mapping is not present.
	 */
	String mapClassName(String namespace, String className);

	/**
	 * Unmap a class name to the mapping currently used at runtime.
	 *
	 * @param targetNamespace The target namespace for unmapping.
	 * @param className The provided class name, in dot-format ("mypackage.MyClass$Inner"),
	 *                  of the mapping form currently used at runtime.
	 * @return The mapped class name, or className if such a mapping is not present.
	 */
	String unmapClassName(String targetNamespace, String className);

	/**
	 * Map a field name to the mapping currently used at runtime.
	 *
	 * @param namespace The namespace of the provided field name.
	 * @param owner The owner of the field, in dot-format ("mypackage.MyClass$Inner").
	 * @param name The name of the field.
	 * @param descriptor The descriptor of the field.
	 * @return The mapped field name, or name if such a mapping is not present.
	 */
	String mapFieldName(String namespace, String owner, String name, String descriptor);

	/**
	 * Map a method name to the mapping currently used at runtime.
	 *
	 * @param namespace The namespace of the provided method name.
	 * @param owner The owner of the method, in dot-format ("mypackage.MyClass$Inner").
	 * @param name The name of the method.
	 * @param descriptor The descriptor of the method.
	 * @return The mapped method name, or name if such a mapping is not present.
	 */
	String mapMethodName(String namespace, String owner, String name, String descriptor);
}
