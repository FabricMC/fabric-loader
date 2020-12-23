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

package net.fabricmc.loader.transformer;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.stitch.annotation.Environment;
import net.fabricmc.stitch.annotation.EnvironmentInterface;
import net.fabricmc.stitch.annotation.EnvironmentInterfaces;
import org.objectweb.asm.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped.
 */
public class EnvironmentStrippingData extends ClassVisitor {
	private static final Set<String> ENVIRONMENT_DESCRIPTORS = ImmutableSet.of(
			Type.getDescriptor(Environment.class),
			Type.getDescriptor(net.fabricmc.api.Environment.class));
	private static final Set<String> ENVIRONMENT_INTERFACE_DESCRIPTORS = ImmutableSet.of(
			Type.getDescriptor(EnvironmentInterface.class),
			Type.getDescriptor(net.fabricmc.api.EnvironmentInterface.class));
	private static final Set<String> ENVIRONMENT_INTERFACES_DESCRIPTORS = ImmutableSet.of(
			Type.getDescriptor(EnvironmentInterfaces.class),
			Type.getDescriptor(net.fabricmc.api.EnvironmentInterfaces.class));

	private final String envType;

	private boolean stripEntireClass = false;
	private final Collection<String> stripInterfaces = new HashSet<>();
	private final Collection<String> stripFields = new HashSet<>();
	private final Collection<String> stripMethods = new HashSet<>();

	private class EnvironmentAnnotationVisitor extends AnnotationVisitor {
		private final Runnable onEnvMismatch;

		private EnvironmentAnnotationVisitor(int api, Runnable onEnvMismatch) {
			super(api);
			this.onEnvMismatch = onEnvMismatch;
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			if ("value".equals(name) && !envType.equals(value)) {
				onEnvMismatch.run();
			}
		}
	}

	private class EnvironmentInterfaceAnnotationVisitor extends AnnotationVisitor {
		private boolean envMismatch;
		private Type itf;

		private EnvironmentInterfaceAnnotationVisitor(int api) {
			super(api);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			if ("value".equals(name) && !envType.equals(value)) {
				envMismatch = true;
			}
		}

		@Override
		public void visit(String name, Object value) {
			if ("itf".equals(name)) {
				itf = (Type) value;
			}
		}

		@Override
		public void visitEnd() {
			if (envMismatch) {
				stripInterfaces.add(itf.getInternalName());
			}
		}
	}

	private AnnotationVisitor visitMemberAnnotation(String descriptor, boolean visible, Runnable onEnvMismatch) {
		if (ENVIRONMENT_DESCRIPTORS.contains(descriptor)) {
			return new EnvironmentAnnotationVisitor(api, onEnvMismatch);
		}
		return null;
	}

	public EnvironmentStrippingData(int api, String envType) {
		super(api);
		this.envType = envType;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (ENVIRONMENT_DESCRIPTORS.contains(descriptor)) {
			return new EnvironmentAnnotationVisitor(api, () -> stripEntireClass = true);
		} else if (ENVIRONMENT_INTERFACE_DESCRIPTORS.contains(descriptor)) {
			return new EnvironmentInterfaceAnnotationVisitor(api);
		} else if (ENVIRONMENT_INTERFACES_DESCRIPTORS.contains(descriptor)) {
			return new AnnotationVisitor(api) {
				@Override
				public AnnotationVisitor visitArray(String name) {
					if ("value".equals(name)) {
						return new AnnotationVisitor(api) {
							@Override
							public AnnotationVisitor visitAnnotation(String name, String descriptor) {
								return new EnvironmentInterfaceAnnotationVisitor(api);
							}
						};
					}
					return null;
				}
			};
		}
		return null;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return new FieldVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return visitMemberAnnotation(descriptor, visible, () -> stripFields.add(name + descriptor));
			}
		};
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		String methodId = name + descriptor;
		return new MethodVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return visitMemberAnnotation(descriptor, visible, () -> stripMethods.add(methodId));
			}
		};
	}

	public boolean stripEntireClass() {
		return stripEntireClass;
	}

	public Collection<String> getStripInterfaces() {
		return stripInterfaces;
	}

	public Collection<String> getStripFields() {
		return stripFields;
	}

	public Collection<String> getStripMethods() {
		return stripMethods;
	}

	public boolean isEmpty() {
		return stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty();
	}
}
