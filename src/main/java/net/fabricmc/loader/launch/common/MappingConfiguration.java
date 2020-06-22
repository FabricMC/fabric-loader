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

package net.fabricmc.loader.launch.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;

import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.LocalVariableDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MappingConfiguration {
	protected static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");

	private static TinyTree mappings;
	private static boolean checkedMappings;

	private static TinyTree wrapTree(TinyTree mappings) {
		return new TinyTree() {
			private ClassDef wrap(ClassDef mapping) {
				return new ClassDef() {
					private Optional<String> remap(String name, String namespace) {
						return Optional.ofNullable(getDefaultNamespaceClassMap().get(name)).map(mapping -> mapping.getRawName(namespace)).map(Strings::emptyToNull);
					}

					private String remapDesc(String desc, String namespace) {
						Type type = Type.getType(desc);

						switch (type.getSort()) {
						case Type.ARRAY: {
							StringBuilder remappedDescriptor = new StringBuilder(desc.substring(0, type.getDimensions() - 1));

							remappedDescriptor.append(remapDesc(type.getElementType().getDescriptor(), namespace));

							return remappedDescriptor.toString();
						}

						case Type.OBJECT:
							return remap(type.getInternalName(), namespace).map(name -> 'L' + name + ';').orElse(desc);

						case Type.METHOD: {
							if ("()V".equals(desc)) return desc;

							StringBuilder stringBuilder = new StringBuilder("(");
							for (Type argumentType : type.getArgumentTypes()) {
								stringBuilder.append(remapDesc(argumentType.getDescriptor(), namespace));
							}

							Type returnType = type.getReturnType();
							if (returnType == Type.VOID_TYPE) {
								stringBuilder.append(")V");
							} else {
								stringBuilder.append(')').append(remapDesc(returnType.getDescriptor(), namespace));
							}

							return stringBuilder.toString();
						}

						default:
							return desc;
						}
					}

					@Override
					public String getRawName(String namespace) {
						try {
							return mapping.getRawName(namespace);
						} catch (ArrayIndexOutOfBoundsException e) {
							return ""; //No name for the namespace
						}
					}

					@Override
					public String getName(String namespace) {
						return mapping.getName(namespace);
					}

					@Override
					public String getComment() {
						return mapping.getComment();
					}

					@Override
					public Collection<MethodDef> getMethods() {
						return Collections2.transform(mapping.getMethods(), method -> new MethodDef() {
							@Override
							public String getRawName(String namespace) {
								try {
									return method.getRawName(namespace);
								} catch (ArrayIndexOutOfBoundsException e) {
									return ""; //No name for the namespace
								}
							}

							@Override
							public String getName(String namespace) {
								return method.getName(namespace);
							}

							@Override
							public String getComment() {
								return method.getComment();
							}

							@Override
							public String getDescriptor(String namespace) {
								String primary = getMetadata().getNamespaces().get(0); //If the namespaces are empty we shouldn't exist
								String desc = method.getDescriptor(primary);
								return primary.equals(namespace) ? desc : remapDesc(desc, namespace);
							}

							@Override
							public Collection<ParameterDef> getParameters() {
								return method.getParameters();
							}

							@Override
							public Collection<LocalVariableDef> getLocalVariables() {
								return method.getLocalVariables();
							}
						});
					}

					@Override
					public Collection<FieldDef> getFields() {
						return Collections2.transform(mapping.getFields(), field -> new FieldDef() {
							@Override
							public String getRawName(String namespace) {
								try {
									return field.getRawName(namespace);
								} catch (ArrayIndexOutOfBoundsException e) {
									return ""; //No name for the namespace
								}
							}

							@Override
							public String getName(String namespace) {
								return field.getName(namespace);
							}

							@Override
							public String getComment() {
								return field.getComment();
							}

							@Override
							public String getDescriptor(String namespace) {
								String primary = getMetadata().getNamespaces().get(0); //If the namespaces are empty we shouldn't exist
								String desc = field.getDescriptor(primary);
								return primary.equals(namespace) ? desc : remapDesc(desc, namespace);
							}
						});
					}
				};
			}

			@Override
			public TinyMetadata getMetadata() {
				return mappings.getMetadata();
			}

			@Override
			public Map<String, ClassDef> getDefaultNamespaceClassMap() {
				return Maps.transformValues(mappings.getDefaultNamespaceClassMap(), this::wrap);
			}

			@Override
			public Collection<ClassDef> getClasses() {
				return Collections2.transform(mappings.getClasses(), this::wrap);
			}
		};
	}

	public TinyTree getMappings() {
		if (!checkedMappings) {
			InputStream mappingStream = FabricLauncherBase.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny");

			if (mappingStream != null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(mappingStream))) {
					long time = System.currentTimeMillis();
					mappings = wrapTree(TinyMappingFactory.loadWithDetection(reader));
					LOGGER.debug("Loading mappings took " + (System.currentTimeMillis() - time) + " ms");
				} catch (IOException ee) {
					ee.printStackTrace();
				}

				try {
					mappingStream.close();
				} catch (IOException ee) {
					ee.printStackTrace();
				}
			}

			if (mappings == null) {
				LOGGER.info("Mappings not present!");
				mappings = TinyMappingFactory.EMPTY_TREE;
			}

			checkedMappings = true;
		}

		return mappings;
	}

	public String getTargetNamespace() {
		return FabricLauncherBase.getLauncher().isDevelopment() ? "named" : "intermediary";
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return getTargetNamespace().equals("named");
	}
}
