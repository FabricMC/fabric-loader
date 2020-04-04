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
		it.add(new VarInsnNode(Opcodes.ALOAD, 0));
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
			// (1.5-1.7?)-: Just find it instantiating itself.
			// (1.6-1.8?)+: an <init> starting with java.io.File can be assumed to be definite

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
			ClassNode gameClass = gameEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, gameEntrypoint);
			if (gameClass == null) {
				throw new RuntimeException("Could not load game class " + gameEntrypoint + "!");
			}

			MethodNode gameMethod = null;
			MethodNode gameConstructor = null;
			AbstractInsnNode lwjglLogNode = null;
			int gameMethodQuality = 0;

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

			if (gameMethod == null) {
				throw new RuntimeException("Could not find game constructor method in " + gameClass.name + "!");
			}

			boolean patched = false;
			debug("Patching game constructor " + gameMethod.name + gameMethod.desc);

			if (type == EnvType.SERVER) {
				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				// Server-side: first argument (or null!) is runDirectory, run at end of init
				moveBefore(it, Opcodes.RETURN);
				// runDirectory
				if (serverHasFile) {
					it.add(new VarInsnNode(Opcodes.ALOAD, 1));
				} else {
					it.add(new InsnNode(Opcodes.ACONST_NULL));
				}
				finishEntrypoint(type, it);
				patched = true;
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
			byte[] bytes = launcher.getClassByteArray(cls);
			ClassReader reader = new ClassReader(bytes);
			return reader.getSuperName().equals(superCls);
		} catch (IOException e) {
			throw new RuntimeException("Failed to check superclass of " + cls, e);
		}
	}
}
