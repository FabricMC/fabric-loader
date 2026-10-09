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

package net.fabricmc.loader.impl.game.minecraft.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.game.minecraft.menu.MenuHooks;
import net.fabricmc.loader.impl.game.minecraft.menu.MenuMappings;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class ModsMenuPatch extends GamePatch {
	static final String SCREEN = "net/fabricmc/loader/impl/game/minecraft/menu/GeneratedModsScreen";
	static final String BUTTON = "net/fabricmc/loader/impl/game/minecraft/menu/GeneratedMenuButton";
	private static final String API = "net/fabricmc/loader/impl/game/minecraft/menu/MenuAdapter";
	private static final String API_DESC = "L" + API + ";";

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> source, Consumer<ClassNode> emitter) {
		if (launcher.getEnvironmentType() != EnvType.CLIENT || "false".equalsIgnoreCase(System.getProperty("fabric.modsMenu"))) return;

		try {
			apply(source, emitter, new MenuMappings(FabricLoader.getInstance().getMappingResolver()));
		} catch (IllegalArgumentException e) {
			Log.warn(LogCategory.GAME_PATCH, "Could not adapt the mods menu to this Minecraft client", e);
		}
	}

	static void apply(Function<String, ClassNode> source, Consumer<ClassNode> emitter, MenuMappings mappings) {
		ClassNode screen = requireClass(source, mappings, "net/minecraft/class_437", "net/minecraft/client/gui/screens/Screen", "net/minecraft/client/gui/screen/Screen");
		ClassNode title = requireClass(source, mappings, "net/minecraft/class_442", "net/minecraft/client/gui/screens/TitleScreen", "net/minecraft/client/gui/screen/TitleScreen");
		MethodNode init = requireMethod(screen, mappings, "()V", "method_25426", "method_2224", "init");
		MethodNode titleInit = requireMethod(title, mappings, "()V", init.name, "method_25426", "method_2224", "init");
		MethodNode close = requireMethod(screen, mappings, "()V", "method_25419", "method_2210", "close", "onClose");
		MethodNode constructor = null;

		for (MethodNode method : screen.methods) {
			if ("<init>".equals(method.name)) {
				Type[] args = Type.getArgumentTypes(method.desc);
				if (args.length == 0 || args.length == 1 && args[0].getSort() == Type.OBJECT) constructor = method;
			}
		}

		if (constructor == null) throw new IllegalArgumentException("No supported Screen constructor in " + screen.name);
		List<MethodNode> renderers = new ArrayList<>();

		for (MethodNode method : screen.methods) {
			if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)) == 0
					&& Type.getReturnType(method.desc).getSort() == Type.VOID
					&& mappings.method(screen.name, method.desc, method.name, "method_25394", "method_2214", "method_18326", "render", "extractRenderState")) {
				renderers.add(method);
			}
		}

		if (renderers.isEmpty()) throw new IllegalArgumentException("No supported Screen renderer in " + screen.name);
		ClassNode generated = screen(screen, constructor, init, close, renderers, mappings);
		inputMethods(generated, screen, source, mappings);
		ClassNode button = findClass(source, mappings, "net/minecraft/class_4185", "net/minecraft/client/gui/components/Button", "net/minecraft/client/gui/widget/ButtonWidget");

		if (button == null) {
			button = requireClass(source, mappings, "net/minecraft/class_339", "net/minecraft/client/gui/widget/ClickableWidget");
		}

		ClassNode legacy = legacyButton(button, source, mappings);
		if (legacy != null) emitter.accept(legacy);

		emitter.accept(generated);

		for (AbstractInsnNode instruction : titleInit.instructions.toArray()) {
			if (instruction.getOpcode() == Opcodes.RETURN) {
				titleInit.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
				titleInit.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME,
						"addTitleButton", "(Ljava/lang/Object;)V", false));
			}
		}

		titleInit.maxStack = Math.max(1, titleInit.maxStack);
		emitter.accept(title);
	}

	private static ClassNode screen(ClassNode parent, MethodNode constructor, MethodNode init, MethodNode close,
			List<MethodNode> renderers, MenuMappings mappings) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, SCREEN, null, parent.name, null);
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "state", "Ljava/lang/Object;", null, null).visitEnd();
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "adapter", API_DESC, null, null).visitEnd();
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;" + API_DESC + ")V", null, null);
		method.visitCode();
		method.visitVarInsn(Opcodes.ALOAD, 0);

		if (!"()V".equals(constructor.desc)) {
			Type titleType = Type.getArgumentTypes(constructor.desc)[0];

			if (!titleType.equals(Type.getType(String.class))) {
				method.visitVarInsn(Opcodes.ALOAD, 2);
			}

			method.visitVarInsn(Opcodes.ALOAD, 1);
			method.visitMethodInsn(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME, "title", "(Ljava/lang/Object;)Ljava/lang/String;", false);

			if (!titleType.equals(Type.getType(String.class))) {
				method.visitMethodInsn(Opcodes.INVOKEINTERFACE, API, "text", "(Ljava/lang/String;)Ljava/lang/Object;", true);
				method.visitTypeInsn(Opcodes.CHECKCAST, titleType.getInternalName());
			}
		}

		method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.name, "<init>", constructor.desc, false);
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitVarInsn(Opcodes.ALOAD, 1);
		method.visitFieldInsn(Opcodes.PUTFIELD, SCREEN, "state", "Ljava/lang/Object;");
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitVarInsn(Opcodes.ALOAD, 2);
		method.visitFieldInsn(Opcodes.PUTFIELD, SCREEN, "adapter", API_DESC);
		finish(method);
		method = writer.visitMethod(Opcodes.ACC_PUBLIC, init.name, "()V", null, null);
		method.visitCode();
		method.visitVarInsn(Opcodes.ALOAD, 0);
		loadState(method);
		loadAdapter(method);
		method.visitMethodInsn(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME, "init", "(Ljava/lang/Object;Ljava/lang/Object;" + API_DESC + ")V", false);
		finish(method);
		method = writer.visitMethod(Opcodes.ACC_PUBLIC, close.name, "()V", null, null);
		method.visitCode();
		loadState(method);
		loadAdapter(method);
		method.visitMethodInsn(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME, "close", "(Ljava/lang/Object;" + API_DESC + ")V", false);
		finish(method);

		for (MethodNode renderer : renderers) {
			method = writer.visitMethod(Opcodes.ACC_PUBLIC, renderer.name, renderer.desc, null, null);
			method.visitCode();
			MethodNode background = null;

			for (MethodNode candidate : parent.methods) {
				if ((candidate.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0
						&& mappings.method(parent.name, candidate.desc, candidate.name, "method_25420", "method_2225", "renderBackground", "extractBackground")
						&& arguments(renderer.desc, candidate.desc) != null) {
					background = candidate;
					break;
				}
			}

			if (background != null && !drawsBackground(parent, renderer, background)) {
				method.visitVarInsn(Opcodes.ALOAD, 0);
				loadArguments(method, renderer.desc, background.desc);
				method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.name, background.name, background.desc, false);
			}

			method.visitVarInsn(Opcodes.ALOAD, 0);
			loadState(method);
			loadAdapter(method);
			Type[] parameters = Type.getArgumentTypes(renderer.desc);
			boolean context = parameters[0].getSort() == Type.OBJECT;

			if (context) {
				method.visitVarInsn(Opcodes.ALOAD, 1);
			} else {
				method.visitInsn(Opcodes.ACONST_NULL);
			}

			int slot = context ? 2 : 1;
			method.visitVarInsn(Opcodes.ILOAD, slot);
			method.visitVarInsn(Opcodes.ILOAD, slot + 1);
			method.visitVarInsn(Opcodes.FLOAD, slot + 2);
			method.visitMethodInsn(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME, "render", "(Ljava/lang/Object;Ljava/lang/Object;" + API_DESC + "Ljava/lang/Object;IIF)V", false);
			method.visitVarInsn(Opcodes.ALOAD, 0);
			loadArguments(method, renderer.desc, renderer.desc);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.name, renderer.name, renderer.desc, false);
			finish(method);
		}

		writer.visitEnd();
		return node(writer);
	}

	private static void inputMethods(ClassNode generated, ClassNode parent, Function<String, ClassNode> source, MenuMappings mappings) {
		Set<String> seen = new HashSet<>();
		Set<String> visited = new HashSet<>();
		List<ClassNode> pending = new ArrayList<>();
		pending.add(parent);

		for (int index = 0; index < pending.size(); index++) {
			ClassNode type = pending.get(index);
			if (type == null || !visited.add(type.name)) continue;

			for (MethodNode original : type.methods) {
				if ((original.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) != 0
						|| !Type.BOOLEAN_TYPE.equals(Type.getReturnType(original.desc)) || !seen.add(original.name + original.desc)) continue;
				String kind = null;
				String[] names = { "mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled" };
				String[] intermediary = { "method_25402", "method_25406", "method_25403", "method_25401" };

				for (int i = 0; i < names.length; i++) {
					if (mappings.method(type.name, original.desc, original.name, names[i], intermediary[i])) kind = names[i];
				}

				if (kind == null) continue;
				ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, SCREEN, null, parent.name, null);
				MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, original.name, original.desc, null, null);
				method.visitCode();
				loadState(method);
				loadAdapter(method);
				method.visitLdcInsn(kind);
				Type[] args = Type.getArgumentTypes(original.desc);
				method.visitLdcInsn(args.length);
				method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
				int slot = 1;

				for (int i = 0; i < args.length; i++) {
					method.visitInsn(Opcodes.DUP);
					method.visitLdcInsn(i);
					method.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
					box(method, args[i]);
					method.visitInsn(Opcodes.AASTORE);
					slot += args[i].getSize();
				}

				method.visitMethodInsn(Opcodes.INVOKESTATIC, MenuHooks.INTERNAL_NAME, "input", "(Ljava/lang/Object;" + API_DESC + "Ljava/lang/String;[Ljava/lang/Object;)Z", false);
				Label delegate = new Label();
				method.visitJumpInsn(Opcodes.IFEQ, delegate);
				method.visitInsn(Opcodes.ICONST_1);
				method.visitInsn(Opcodes.IRETURN);
				method.visitLabel(delegate);
				method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				method.visitVarInsn(Opcodes.ALOAD, 0);
				loadArguments(method, original.desc, original.desc);
				method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.name, original.name, original.desc, false);
				method.visitInsn(Opcodes.IRETURN);
				method.visitMaxs(0, 0);
				method.visitEnd();
				generated.methods.add(node(writer).methods.get(0));
			}

			if (type.superName != null) pending.add(source.apply(type.superName.replace('/', '.')));
			for (String contract : type.interfaces) pending.add(source.apply(contract.replace('/', '.')));
		}
	}

	private static void box(MethodVisitor method, Type type) {
		String owner;

		switch (type.getSort()) {
		case Type.BOOLEAN: owner = "java/lang/Boolean"; break;
		case Type.INT: owner = "java/lang/Integer"; break;
		case Type.DOUBLE: owner = "java/lang/Double"; break;
		case Type.FLOAT: owner = "java/lang/Float"; break;
		case Type.LONG: owner = "java/lang/Long"; break;
		default: return;
		}

		method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf", "(" + type.getDescriptor() + ")L" + owner + ";", false);
	}

	static boolean drawsBackground(ClassNode screen, MethodNode renderer, MethodNode background) {
		for (MethodNode method : screen.methods) {
			boolean callsRenderer = method == renderer;
			boolean callsBackground = false;

			for (AbstractInsnNode instruction : method.instructions) {
				if (!(instruction instanceof MethodInsnNode)) continue;
				MethodInsnNode call = (MethodInsnNode) instruction;
				if (!screen.name.equals(call.owner)) continue;
				callsRenderer |= renderer.name.equals(call.name) && renderer.desc.equals(call.desc);
				callsBackground |= background.name.equals(call.name) && background.desc.equals(call.desc);
			}

			if (callsRenderer && callsBackground) return true;
		}

		return false;
	}

	private static ClassNode legacyButton(ClassNode button, Function<String, ClassNode> source, MenuMappings mappings) {
		MethodNode constructor = null;

		for (MethodNode method : button.methods) {
			Type[] args = Type.getArgumentTypes(method.desc);

			if ("<init>".equals(method.name) && (args.length == 5 || args.length == 6) && args[args.length - 1].getSort() == Type.OBJECT) {
				boolean ints = true;

				for (int i = 0; i < args.length - 1; i++) {
					ints &= args[i].getSort() == Type.INT;
				}

				if (ints) constructor = method;
			}
		}

		if (constructor == null) return null;
		MethodNode click = null;

		for (ClassNode type = button; type != null; type = source.apply(type.superName.replace('/', '.'))) {
			for (MethodNode method : type.methods) {
				if (mappings.method(type.name, method.desc, method.name, "method_25306", "method_1826", "onPress", "onClick")
						&& Type.getReturnType(method.desc).getSort() == Type.VOID) click = method;
			}

			if (click != null || type.superName == null) break;
		}

		if (click == null) throw new IllegalArgumentException("No legacy button click method in " + button.name);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, BUTTON, null, button.name, null);
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "action", "Ljava/lang/Runnable;", null, null).visitEnd();
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IIIILjava/lang/String;Ljava/lang/Runnable;" + API_DESC + ")V", null, null);
		method.visitCode();
		method.visitVarInsn(Opcodes.ALOAD, 0);
		Type[] nativeArguments = Type.getArgumentTypes(constructor.desc);

		if (nativeArguments.length == 6) {
			method.visitInsn(Opcodes.ICONST_0);
		}

		for (int i = 1; i <= 4; i++) {
			method.visitVarInsn(Opcodes.ILOAD, i);
		}

		Type label = nativeArguments[nativeArguments.length - 1];

		if (label.equals(Type.getType(String.class))) {
			method.visitVarInsn(Opcodes.ALOAD, 5);
		} else {
			method.visitVarInsn(Opcodes.ALOAD, 7);
			method.visitVarInsn(Opcodes.ALOAD, 5);
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, API, "text", "(Ljava/lang/String;)Ljava/lang/Object;", true);
			method.visitTypeInsn(Opcodes.CHECKCAST, label.getInternalName());
		}

		method.visitMethodInsn(Opcodes.INVOKESPECIAL, button.name, "<init>", constructor.desc, false);
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitVarInsn(Opcodes.ALOAD, 6);
		method.visitFieldInsn(Opcodes.PUTFIELD, BUTTON, "action", "Ljava/lang/Runnable;");
		finish(method);
		method = writer.visitMethod(Opcodes.ACC_PUBLIC, click.name, click.desc, null, null);
		method.visitCode();
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitFieldInsn(Opcodes.GETFIELD, BUTTON, "action", "Ljava/lang/Runnable;");
		method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
		finish(method);
		writer.visitEnd();
		return node(writer);
	}

	private static void loadState(MethodVisitor method) {
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitFieldInsn(Opcodes.GETFIELD, SCREEN, "state", "Ljava/lang/Object;");
	}

	private static void loadAdapter(MethodVisitor method) {
		method.visitVarInsn(Opcodes.ALOAD, 0);
		method.visitFieldInsn(Opcodes.GETFIELD, SCREEN, "adapter", API_DESC);
	}

	private static int[] arguments(String from, String to) {
		Type[] source = Type.getArgumentTypes(from);
		Type[] target = Type.getArgumentTypes(to);
		int[] indices = new int[target.length];
		int cursor = 0;
		int slot = 1;

		for (int i = 0; i < target.length; i++) {
			while (cursor < source.length && !source[cursor].equals(target[i])) {
				slot += source[cursor++].getSize();
			}

			if (cursor == source.length) return null;
			indices[i] = slot;
			slot += source[cursor++].getSize();
		}

		return indices;
	}

	private static void loadArguments(MethodVisitor method, String from, String to) {
		int[] indices = arguments(from, to);
		Type[] types = Type.getArgumentTypes(to);

		for (int i = 0; i < types.length; i++) {
			method.visitVarInsn(types[i].getOpcode(Opcodes.ILOAD), indices[i]);
		}
	}

	private static void finish(MethodVisitor method) {
		method.visitInsn(Opcodes.RETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	private static ClassNode node(ClassWriter writer) {
		ClassNode node = new ClassNode();
		new ClassReader(writer.toByteArray()).accept(node, 0);
		return node;
	}

	private static MethodNode requireMethod(ClassNode owner, MenuMappings mappings, String descriptor, String... names) {
		for (MethodNode method : owner.methods) {
			if (method.desc.equals(descriptor) && mappings.method(owner.name, method.desc, method.name, names)) return method;
		}

		throw new IllegalArgumentException("No " + String.join("/", names) + descriptor + " in " + owner.name);
	}

	private static ClassNode requireClass(Function<String, ClassNode> source, MenuMappings mappings, String... names) {
		ClassNode node = findClass(source, mappings, names);
		if (node == null) throw new IllegalArgumentException("Missing " + String.join("/", names));
		return node;
	}

	private static ClassNode findClass(Function<String, ClassNode> source, MenuMappings mappings, String... names) {
		for (String name : names) {
			ClassNode node = source.apply(mappings.className(name).replace('/', '.'));
			if (node != null) return node;
		}

		return null;
	}
}
