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

package net.fabricmc.loader.entrypoint.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchHook extends EntrypointPatch {
	public EntrypointPatchHook(EntrypointTransformer transformer) {
		super(transformer);
	}

	private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
		it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/entrypoint/minecraft/hooks/Entrypoint" + (type == EnvType.CLIENT ? "Client" : "Server"), "start", "(Ljava/io/File;Ljava/lang/Object;)V", false));
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		EnvType type = launcher.getEnvironmentType();
		String entrypoint = launcher.getEntrypoint();

		if (!entrypoint.startsWith("net.minecraft.") && !entrypoint.startsWith("com.mojang.")) {
			return;
		}

		try {
			String gameEntrypoint = null;
			boolean serverHasFile = true;
			boolean isApplet = entrypoint.contains("Applet");
			ClassNode mainClass = loadClass(launcher, entrypoint);

			if (mainClass == null) {
				throw new RuntimeException("Could not load main class " + entrypoint + "!");
			}

			// Main -> Game entrypoint search
			//
			// -- CLIENT --
			// pre-1.6 (seems to hold to 0.0.11!): find the only non-static non-java-packaged Object field
			// 1.6.1+: [client].start() [INVOKEVIRTUAL]
			// 19w04a: [client].<init> [INVOKESPECIAL] -> Thread.start()
			// -- SERVER --
			// (1.5-1.7?)-:     Just find it instantiating itself.
			// (1.6-1.8?)+:     an <init> starting with java.io.File can be assumed to be definite
			// (20w20b-20w21a): Now has its own main class, that constructs the server class. Find a specific regex string in the class.
			// (20w22a)+:       Datapacks are now reloaded in main. To ensure that mods load correctly, inject into Main after --safeMode check.

			boolean is20w22aServerOrHigher = false;

			if (type == EnvType.CLIENT) {
				// pre-1.6 route
				List<FieldNode> newGameFields = findFields(mainClass,
						(f) -> !isStatic(f.access) && f.desc.startsWith("L") && !f.desc.startsWith("Ljava/")
						);

				if (newGameFields.size() == 1) {
					gameEntrypoint = Type.getType(newGameFields.get(0).desc).getClassName();
				}
			}

			if (gameEntrypoint == null) {
				// main method searches
				MethodNode mainMethod = findMethod(mainClass, (method) -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") && isPublicStatic(method.access));
				if (mainMethod == null) {
					throw new RuntimeException("Could not find main method in " + entrypoint + "!");
				}

				if (type == EnvType.SERVER) {
					// pre-1.6 method search route
					MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(mainMethod,
							(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") && ((MethodInsnNode) insn).owner.equals(mainClass.name),
							false
							);

					if (newGameInsn != null) {
						gameEntrypoint = newGameInsn.owner.replace('/', '.');
						serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;");
					}
				}

				if (gameEntrypoint == null) {
					// modern method search routes
					MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(mainMethod,
							type == EnvType.CLIENT
							? (insn) -> (insn.getOpcode() == Opcodes.INVOKESPECIAL || insn.getOpcode() == Opcodes.INVOKEVIRTUAL) && !((MethodInsnNode) insn).owner.startsWith("java/")
									: (insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") && hasSuperClass(((MethodInsnNode) insn).owner, mainClass.name, launcher),
									true
							);

					// New 20w20b way of finding the server constructor
					if (newGameInsn == null && type == EnvType.SERVER) {
						newGameInsn = (MethodInsnNode) findInsn(mainMethod, insn -> (insn instanceof MethodInsnNode) && insn.getOpcode() == Opcodes.INVOKESPECIAL && hasStrInMethod(((MethodInsnNode)insn).owner, "<clinit>", "()V", "^[a-fA-F0-9]{40}$", launcher), false);
					}

					// Detect 20w22a by searching for a specific log message
					if(type == EnvType.SERVER && hasStrInMethod(mainClass.name, mainMethod.name, mainMethod.desc, "Safe mode active, only vanilla datapack will be loaded", launcher)) {
						is20w22aServerOrHigher = true;
						gameEntrypoint = mainClass.name;
					}

					if (newGameInsn != null) {
						gameEntrypoint = newGameInsn.owner.replace('/', '.');
						serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;");
					}
				}
			}

			if (gameEntrypoint == null) {
				throw new RuntimeException("Could not find game constructor in " + entrypoint + "!");
			}

			debug("Found game constructor: " + entrypoint + " -> " + gameEntrypoint);
			ClassNode gameClass = gameEntrypoint.equals(entrypoint) || is20w22aServerOrHigher ? mainClass : loadClass(launcher, gameEntrypoint);
			if (gameClass == null) {
				throw new RuntimeException("Could not load game class " + gameEntrypoint + "!");
			}

			MethodNode gameMethod = null;
			MethodNode gameConstructor = null;
			AbstractInsnNode lwjglLogNode = null;
			int gameMethodQuality = 0;

			if(!is20w22aServerOrHigher) {
				for (MethodNode gmCandidate : gameClass.methods) {
					if (gmCandidate.name.equals("<init>")) {
						gameConstructor = gmCandidate;
						if (gameMethodQuality < 1) {
							gameMethod = gmCandidate;
							gameMethodQuality = 1;
						}
					}
					if (type == EnvType.CLIENT && !isApplet && gameMethodQuality < 2) {
						// Try to find a method with an LDC string "LWJGL Version: ".
						// This is the "init()" method, or as of 19w38a is the constructor, or called somewhere in that vicinity,
						// and is by far superior in hooking into for a well-off mod start.

						int qual = 2;
						boolean hasLwjglLog = false;
						ListIterator<AbstractInsnNode> it = gmCandidate.instructions.iterator();
						while (it.hasNext()) {
							AbstractInsnNode insn = it.next();
							if (insn instanceof LdcInsnNode) {
								Object cst = ((LdcInsnNode) insn).cst;
								if (cst instanceof String) {
									String s = (String) cst;
									//This log output was renamed to Backend library in 19w34a
									if (s.startsWith("LWJGL Version: ") || s.startsWith("Backend library: ")) {
										hasLwjglLog = true;
										if ("LWJGL Version: ".equals(s) || "LWJGL Version: {}".equals(s) || "Backend library: {}".equals(s)) {
											qual = 3;
											lwjglLogNode = insn;
										}
										break;
									}
								}
							}
						}

						if (hasLwjglLog) {
							gameMethod = gmCandidate;
							gameMethodQuality = qual;
						}
					}
				}
			} else {
				gameMethod = findMethod(mainClass, (method) -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") && isPublicStatic(method.access));
			}

			if (gameMethod == null) {
				throw new RuntimeException("Could not find game constructor method in " + gameClass.name + "!");
			}

			boolean patched = false;
			debug("Patching game constructor " + gameMethod.name + gameMethod.desc);

			if (type == EnvType.SERVER) {
				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				if(!is20w22aServerOrHigher) {
					// Server-side: first argument (or null!) is runDirectory, run at end of init
					moveBefore(it, Opcodes.RETURN);
					// runDirectory
					if (serverHasFile) {
						it.add(new VarInsnNode(Opcodes.ALOAD, 1));
					} else {
						it.add(new InsnNode(Opcodes.ACONST_NULL));
					}
					it.add(new VarInsnNode(Opcodes.ALOAD, 0));

					finishEntrypoint(type, it);
					patched = true;
				} else {
					// Server-side: Run before `server.properties` is loaded so early logic like world generation is not broken due to being loaded by server properties before mods are initialized.
					// ----------------
					// ldc "server.properties"
					// iconst_0
					// anewarray java/lang/String
					// invokestatic java/nio/file/Paths.get (Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;
					// ----------------
					debug("20w22a+ detected, patching main method...");

					// Find the "server.properties".
					LdcInsnNode serverPropertiesLdc = (LdcInsnNode) findInsn(gameMethod, insn -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst.equals("server.properties"), false);

					// Move before the `server.properties` ldc is pushed onto stack
					moveBefore(it, serverPropertiesLdc);

					// Detect if we are running exactly 20w22a.
					// Find the synthetic method where dedicated server instance is created so we can set the game instance.
					// This cannot be the main method, must be static (all methods are static, so useless to check)
					// Cannot return a void or boolean
					// Is only method that returns a class instance
					// If we do not find this, then we are certain this is 20w22a.
					MethodNode serverStartMethod = findMethod(mainClass, method -> {
						if (method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")) {
							return false;
						}

						final Type methodReturnType = Type.getReturnType(method.desc);

						return methodReturnType.getSort() != Type.BOOLEAN && methodReturnType.getSort() != Type.VOID && methodReturnType.getSort() == Type.OBJECT;
					});

					if (serverStartMethod == null) {
						// We are running 20w22a, this requires a separate process for capturing game instance
						debug("Detected 20w22a");
					} else {
						debug("Detected version above 20w22a");
						// We are not running 20w22a.
						// This means we need to position ourselves before any dynamic registries are initialized.
						// Since it is a bit hard to figure out if we are on most 1.16-pre1+ versions.
						// So if the version is below 1.16.2-pre2, this injection will be before the timer thread hack. This should have no adverse effects.

						// This diagram shows the intended result for 1.16.2-pre2
						// ----------------
						// invokestatic ... Bootstrap log missing
						// <---- target here (1.16-pre1 to 1.16.2-pre1)
						// ...misc
						// invokestatic ... (Timer Thread Hack)
						// <---- target here (1.16.2-pre2+)
						// ... misc
						// invokestatic ... (Registry Manager) [Only present in 1.16.2-pre2+]
						// ldc "server.properties"
						// ----------------

						// The invokestatic insn we want is just before the ldc
						AbstractInsnNode previous = serverPropertiesLdc.getPrevious();

						while (true) {
							if (previous == null) {
								throw new RuntimeException("Failed to find static method before loading server properties");
							}

							if (previous.getOpcode() == Opcodes.INVOKESTATIC) {
								break;
							}

							previous = previous.getPrevious();
						}

						boolean foundNode = false;

						// Move the iterator back till we are just before the insn node we wanted
						while (it.hasPrevious()) {
							if (it.previous() == previous) {
								if (it.hasPrevious()) {
									foundNode = true;
									// Move just before the method insn node
									it.previous();
								}

								break;
							}
						}

						if (!foundNode) {
							throw new RuntimeException("Failed to find static method before loading server properties");
						}
					}

					it.add(new InsnNode(Opcodes.ACONST_NULL));

					// Pass null for now, we will set the game instance when the dedicated server is created.
					it.add(new InsnNode(Opcodes.ACONST_NULL));

					finishEntrypoint(type, it); // Inject the hook entrypoint.

					// Time to find the dedicated server ctor to capture game instance
					if (serverStartMethod == null) {
						// FIXME: For 20w22a, find the only constructor in the game method that takes a DataFixer.
						// That is the guaranteed to be dedicated server constructor
						debug("Server game instance has not be implemented yet for 20w22a");
					} else {
						final ListIterator<AbstractInsnNode> serverStartIt = serverStartMethod.instructions.iterator();

						// 1.16-pre1+ Find the only constructor which takes a Thread as it's first parameter
						MethodInsnNode dedicatedServerConstructor = (MethodInsnNode) findInsn(serverStartMethod, insn -> {
							if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("<init>")) {
								Type constructorType = Type.getMethodType(((MethodInsnNode) insn).desc);

								if (constructorType.getArgumentTypes().length <= 0) {
									return false;
								}

								return constructorType.getArgumentTypes()[0].getDescriptor().equals("Ljava/lang/Thread;");
							}

							return false;
						}, false);

						if (dedicatedServerConstructor == null) {
							throw new RuntimeException("Could not find dedicated server constructor");
						}

						// Jump after the <init> call
						moveAfter(serverStartIt, dedicatedServerConstructor);

						// Duplicate dedicated server instance for loader
						serverStartIt.add(new InsnNode(Opcodes.DUP));
						serverStartIt.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/fabricmc/loader/FabricLoader", "INSTANCE", "Lnet/fabricmc/loader/FabricLoader;"));
						serverStartIt.add(new InsnNode(Opcodes.SWAP));
						serverStartIt.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/fabricmc/loader/FabricLoader", "setGameInstance", "(Ljava/lang/Object;)V", false));
					}

					patched = true;
				}
			} else if (type == EnvType.CLIENT && isApplet) {
				// Applet-side: field is private static File, run at end
				// At the beginning, set file field (hook)
				FieldNode runDirectory = findField(gameClass, (f) -> isStatic(f.access) && f.desc.equals("Ljava/io/File;"));
				if (runDirectory == null) {
					// TODO: Handle pre-indev versions.
					//
					// Classic has no agreed-upon run directory.
					// - level.dat is always stored in CWD. We can assume CWD is set, launchers generally adhere to that.
					// - options.txt in newer Classic versions is stored in user.home/.minecraft/. This is not currently handled,
					// but as these versions are relatively low on options this is not a huge concern.
					warn("Could not find applet run directory! (If you're running pre-late-indev versions, this is fine.)");

					ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
					if (gameConstructor == gameMethod) {
						moveBefore(it, Opcodes.RETURN);
					}

					/*							it.add(new TypeInsnNode(Opcodes.NEW, "java/io/File"));
					it.add(new InsnNode(Opcodes.DUP));
					it.add(new LdcInsnNode("."));
					it.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)); */
					it.add(new InsnNode(Opcodes.ACONST_NULL));
					it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/entrypoint/applet/AppletMain", "hookGameDir", "(Ljava/io/File;)Ljava/io/File;", false));
					it.add(new VarInsnNode(Opcodes.ALOAD, 0));
					finishEntrypoint(type, it);
				} else {
					// Indev and above.
					ListIterator<AbstractInsnNode> it = gameConstructor.instructions.iterator();
					moveAfter(it, Opcodes.INVOKESPECIAL); /* Object.init */
					it.add(new FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));
					it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/entrypoint/applet/AppletMain", "hookGameDir", "(Ljava/io/File;)Ljava/io/File;", false));
					it.add(new FieldInsnNode(Opcodes.PUTSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));

					it = gameMethod.instructions.iterator();
					if (gameConstructor == gameMethod) {
						moveBefore(it, Opcodes.RETURN);
					}
					it.add(new FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));
					it.add(new VarInsnNode(Opcodes.ALOAD, 0));
					finishEntrypoint(type, it);
				}
				patched = true;
			} else {
				// Client-side:
				// - if constructor, identify runDirectory field + location, run immediately after
				// - if non-constructor (init method), head

				if (gameConstructor == null) {
					throw new RuntimeException("Non-applet client-side, but could not find constructor?");
				}

				ListIterator<AbstractInsnNode> consIt = gameConstructor.instructions.iterator();
				while (consIt.hasNext()) {
					AbstractInsnNode insn = consIt.next();
					if (insn.getOpcode() == Opcodes.PUTFIELD
							&& ((FieldInsnNode) insn).desc.equals("Ljava/io/File;")) {
						debug("Run directory field is thought to be " + ((FieldInsnNode) insn).owner + "/" + ((FieldInsnNode) insn).name);

						ListIterator<AbstractInsnNode> it;
						if (gameMethod == gameConstructor) {
							it = consIt;
						} else {
							it = gameMethod.instructions.iterator();
						}
						if (lwjglLogNode != null) {
							moveBefore(it, lwjglLogNode);
							for (int i = 0; i < 4; i++) {
								moveBeforeType(it, AbstractInsnNode.METHOD_INSN);
							}
						}
						it.add(new VarInsnNode(Opcodes.ALOAD, 0));
						it.add(new FieldInsnNode(Opcodes.GETFIELD, ((FieldInsnNode) insn).owner, ((FieldInsnNode) insn).name, ((FieldInsnNode) insn).desc));
						it.add(new VarInsnNode(Opcodes.ALOAD, 0));
						finishEntrypoint(type, it);

						patched = true;
						break;
					}
				}
			}

			if (!patched) {
				throw new RuntimeException("Game constructor patch not applied!");
			}

			if (gameClass != mainClass) {
				classEmitter.accept(gameClass);
			} else {
				classEmitter.accept(mainClass);
			}

			if (isApplet) {
				EntrypointTransformer.appletMainClass = entrypoint;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean hasSuperClass(String cls, String superCls, FabricLauncher launcher) {
		if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
			return false;
		}

		try {
			byte[] bytes = launcher.getClassByteArray(cls, false);
			ClassReader reader = new ClassReader(bytes);
			return reader.getSuperName().equals(superCls);
		} catch (IOException e) {
			throw new RuntimeException("Failed to check superclass of " + cls, e);
		}
	}

	private boolean hasStrInMethod(String cls, String methodName, String methodDesc, String str, FabricLauncher launcher) {
		if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
			return false;
		}

		try {
			byte[] bytes = launcher.getClassByteArray(cls, false);
			ClassReader reader = new ClassReader(bytes);
			ClassNode node = new ClassNode();
			reader.accept(node, 0);

			for (MethodNode method : node.methods) {
				if (method.name.equals(methodName) && method.desc.equals(methodDesc)) {
					for (AbstractInsnNode insn : method.instructions) {
						if (insn instanceof LdcInsnNode) {
							Object cst = ((LdcInsnNode) insn).cst;
							if (cst instanceof String) {
								if (cst.equals(str)) {
									return true;
								}
							}
						}
					}
					break;
				}
			}

			return false;
		} catch (IOException e) {
			throw new RuntimeException("Failed to find string in " + cls + methodName + methodDesc, e);
		}
	}
}
