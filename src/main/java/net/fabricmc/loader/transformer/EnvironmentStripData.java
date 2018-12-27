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

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import org.objectweb.asm.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped.
 */
public class EnvironmentStripData extends ClassVisitor {
	// TODO: Remove this
	private static final Set<String> NAUGHTY_INTERFACES = new HashSet<>();
	static {
		NAUGHTY_INTERFACES.add("net/minecraft/class_2618");
		NAUGHTY_INTERFACES.add("net/minecraft/class_3856");
		NAUGHTY_INTERFACES.add("net/minecraft/class_3851");
		NAUGHTY_INTERFACES.add("net/minecraft/client/block/ChestAnimationProgress");
		NAUGHTY_INTERFACES.add("net/minecraft/village/VillagerDataContainer");
	}

	private static final String ENVIRONMENT_DESCRIPTOR = Type.getDescriptor(Environment.class);
	private static final String ENVIRONMENT_INTERFACE_DESCRIPTOR = Type.getDescriptor(EnvironmentInterface.class);

	private final String envType;

	private boolean stripEntireClass = false;
	private final Set<String> stripInterfaces = new HashSet<>();
	private final Set<String> stripFields = new HashSet<>();
	private final Set<String> stripMethods = new HashSet<>();

	private AnnotationVisitor visitAnnotation(String descriptor, boolean visible, Runnable onEnvMismatch) {
		if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new AnnotationVisitor(api) {
				@Override
				public void visitEnum(String name, String descriptor, String value) {
					if ("value".equals(name) && !envType.equals(value)) {
						onEnvMismatch.run();
					}
				}
			};
		}
		return null;
	}

	public EnvironmentStripData(int api, String envType) {
		super(api);
		this.envType = envType;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if (!"CLIENT".equals(envType)) {
			for (String itf : interfaces) {
				if (NAUGHTY_INTERFACES.contains(itf)) {
					this.stripInterfaces.add(itf);
				}
			}
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
            return new AnnotationVisitor(api) {
                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    if ("value".equals(name) && !envType.equals(value)) {
                    	stripEntireClass = true;
                    }
                }
            };
        } else if (ENVIRONMENT_INTERFACE_DESCRIPTOR.equals(descriptor)) {
			return new AnnotationVisitor(api) {
				private boolean envMismatch;

				@Override
				public void visitEnum(String name, String descriptor, String value) {
					if ("value".equals(name) && !envType.equals(value)) {
						envMismatch = true;
					}
				}

				@Override
				public AnnotationVisitor visitArray(String name) {
					if ("itf".equals(name)) {
						return new AnnotationVisitor(api) {
							@Override
							public void visit(String name, Object value) {
								if (envMismatch) {
									stripInterfaces.add(((Type) value).getInternalName());
								}
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
				return EnvironmentStripData.this.visitAnnotation(descriptor, visible, () -> stripFields.add(name + descriptor));
			}
		};
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		String methodId = name + descriptor;
		return new MethodVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return EnvironmentStripData.this.visitAnnotation(descriptor, visible, () -> stripMethods.add(methodId));
			}
		};
	}

	public boolean stripEntireClass() {
		return stripEntireClass;
	}

	public Set<String> getStripInterfaces() {
		return stripInterfaces;
	}

	public Set<String> getStripFields() {
		return stripFields;
	}

	public Set<String> getStripMethods() {
		return stripMethods;
	}

	public boolean isEmpty() {
		return stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty();
	}
}
