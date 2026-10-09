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

package net.fabricmc.loader.impl.game.minecraft.menu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class MenuRuntimeCheck {
	private MenuRuntimeCheck() {
	}

	public static void check(Map<String, ClassNode> classes, Map<String, ClassNode> generated) throws ReflectiveOperationException {
		SchemaLoader loader = new SchemaLoader(classes, generated);
		MinecraftMenuAdapter api = new MinecraftMenuAdapter(loader, new MenuMappings(null));
		Object parent = new Object();
		MenuHooks.State state = new MenuHooks.State(parent, new ModListModel(Collections.emptyList()), null);
		Object screen = loader.loadClass("net.fabricmc.loader.impl.game.minecraft.menu.GeneratedModsScreen").getConstructor(Object.class, MenuAdapter.class).newInstance(state, new ScreenAdapter(api));
		if (api.width(screen) != 320 || api.height(screen) != 240) throw new IllegalStateException("Screen dimensions did not bind");
		MenuHooks.init(screen, state, new ScreenAdapter(api));
		Field clientField = MinecraftMenuAdapter.class.getDeclaredField("client");
		clientField.setAccessible(true);
		Object client = clientField.get(api);
		Field fontField = api.field(client.getClass(), "field_1772", "font", "textRenderer");
		fontField.set(client, fontField.getType().getConstructor().newInstance());

		for (Class<?> owner = screen.getClass(); owner != null; owner = owner.getSuperclass()) {
			for (Field field : owner.getDeclaredFields()) {
				if (field.getType() == List.class) {
					field.setAccessible(true);
					field.set(screen, new ArrayList<>());
				}
			}
		}

		Object search = api.search(screen, 8, 24, 100, 20, "");
		api.searchValue(search);
		api.renderSearch(search, null, 0, 0, 0);

		Object button = api.button(5, 10, 100, 20, "Mods", () -> { });
		api.add(screen, button);
		api.active(button, false);
		api.open(screen);
		MenuMappings names = new MenuMappings(null);

		for (Method method : screen.getClass().getDeclaredMethods()) {
			String descriptor = Type.getMethodDescriptor(method);

			if (names.method(Type.getInternalName(screen.getClass()), descriptor, method.getName(), "method_25394", "method_2214", "method_18326", "render", "extractRenderState")) {
				Object[] arguments = new Object[method.getParameterCount()];
				Class<?>[] types = method.getParameterTypes();

				for (int i = 0; i < types.length; i++) {
					if (!types[i].isPrimitive()) {
						arguments[i] = types[i].getConstructor().newInstance();
					} else if (types[i] == int.class) {
						arguments[i] = Integer.valueOf(0);
					} else if (types[i] == float.class) {
						arguments[i] = Float.valueOf(0.0F);
					}
				}

				method.invoke(screen, arguments);

				if (classes.containsKey(fontField.getType().getName())) {
					Object context = types[0].isPrimitive() ? null : arguments[0];
					MenuCanvas canvas = api.canvas(screen, context);
					canvas.fill(0, 0, 32, 32, 0xffffffff);
					canvas.text("Mods", 8, 8, 0xffffffff);
					canvas.width("Mods");
				}
			}
		}
	}

	private static final class ScreenAdapter implements MenuAdapter {
		private final MenuAdapter delegate;

		ScreenAdapter(MenuAdapter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Object text(String text) {
			return delegate.text(text);
		}

		@Override
		public Object screen(Object state) {
			return delegate.screen(state);
		}

		@Override
		public Object button(int x, int y, int width, int height, String label, Runnable action) {
			return delegate.button(x, y, width, height, label, action);
		}

		@Override
		public void add(Object screen, Object button) {
			delegate.add(screen, button);
		}

		@Override
		public int width(Object screen) {
			return delegate.width(screen);
		}

		@Override
		public int height(Object screen) {
			return delegate.height(screen);
		}

		@Override
		public void open(Object screen) {
			delegate.open(screen);
		}

		@Override
		public void active(Object button, boolean active) {
			delegate.active(button, active);
		}
	}

	private static final class SchemaLoader extends ClassLoader {
		private final Map<String, ClassNode> classes;
		private final Map<String, byte[]> generated = new HashMap<>();

		SchemaLoader(Map<String, ClassNode> classes, Map<String, ClassNode> generated) {
			super(MenuRuntimeCheck.class.getClassLoader());
			this.classes = classes;

			for (ClassNode node : generated.values()) {
				if (!node.name.startsWith("net/fabricmc/loader/impl/game/minecraft/menu/Generated")) continue;
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				this.generated.put(node.name.replace('/', '.'), writer.toByteArray());
			}
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] bytes = generated.get(name);

			if (bytes == null) {
				ClassNode node = classes.get(name);

				if (node == null) {
					if (name.matches("net\\.minecraft\\.class_(437|442|4185|339|342|310|2561|2585|1074)")
							|| name.equals("net.minecraft.client.gui.components.Button") || name.equals("net.minecraft.client.gui.widget.ButtonWidget")
							|| name.equals("net.minecraft.client.gui.screens.Screen") || name.equals("net.minecraft.client.gui.screen.Screen")
							|| name.equals("net.minecraft.client.Minecraft") || name.equals("net.minecraft.client.MinecraftClient")
							|| name.equals("net.minecraft.network.chat.Component") || name.equals("net.minecraft.text.Text")
							|| name.equals("net.minecraft.text.LiteralText") || name.equals("net.minecraft.network.chat.TextComponent")) {
						throw new ClassNotFoundException(name);
					}

					node = new ClassNode();
					node.name = name.replace('.', '/');
					node.superName = "java/lang/Object";

					for (ClassNode owner : classes.values()) {
						if (owner.interfaces.contains(node.name)) node.access = Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
					}
				}

				bytes = stub(node);
			}

			return defineClass(name, bytes, 0, bytes.length);
		}

		private byte[] stub(ClassNode node) {
			boolean iface = (node.access & Opcodes.ACC_INTERFACE) != 0;
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | (iface ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0), node.name, null,
					node.superName == null ? "java/lang/Object" : node.superName, node.interfaces.toArray(new String[0]));

			for (FieldNode field : node.fields) {
				writer.visitField(iface ? field.access : field.access & ~Opcodes.ACC_FINAL, field.name, field.desc, null, null).visitEnd();
			}

			boolean defaultConstructor = false;

			for (MethodNode original : node.methods) {
				if ("<clinit>".equals(original.name)) continue;
				if ("<init>".equals(original.name) && "()V".equals(original.desc)) defaultConstructor = true;
				int access = original.access & ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);

				if (iface && (original.access & Opcodes.ACC_ABSTRACT) != 0) {
					writer.visitMethod(original.access, original.name, original.desc, null, null).visitEnd();
					continue;
				}

				MethodVisitor method = writer.visitMethod(access, original.name, original.desc, null, null);
				method.visitCode();

				if ("<init>".equals(original.name)) {
					constructor(method, node);
					storeArguments(method, node, original.desc);
					method.visitInsn(Opcodes.RETURN);
				} else {
					result(method, node, original);
				}

				method.visitMaxs(0, 0);
				method.visitEnd();
			}

			if (!iface && !defaultConstructor) {
				MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
				method.visitCode();
				constructor(method, node);
				method.visitInsn(Opcodes.RETURN);
				method.visitMaxs(0, 0);
				method.visitEnd();
			}

			writer.visitEnd();
			return writer.toByteArray();
		}

		private void constructor(MethodVisitor method, ClassNode node) {
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, node.superName == null ? "java/lang/Object" : node.superName, "<init>", "()V", false);

			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_STATIC) != 0 || !"I".equals(field.desc)) continue;
				int value = 0;
				if (field.name.equals("width") || field.name.equals("field_22789") || field.name.equals("field_2561")) value = 320;
				if (field.name.equals("height") || field.name.equals("field_22790") || field.name.equals("field_2559")) value = 240;
				method.visitVarInsn(Opcodes.ALOAD, 0);
				method.visitLdcInsn(value);
				method.visitFieldInsn(Opcodes.PUTFIELD, node.name, field.name, "I");
			}
		}

		private void storeArguments(MethodVisitor method, ClassNode node, String descriptor) {
			Type[] args = Type.getArgumentTypes(descriptor);
			int slot = 1;

			for (Type arg : args) {
				if (arg.getSort() == Type.OBJECT) {
					for (FieldNode field : node.fields) {
						if ((field.access & Opcodes.ACC_STATIC) == 0 && field.desc.equals(arg.getDescriptor())) {
							method.visitVarInsn(Opcodes.ALOAD, 0);
							method.visitVarInsn(Opcodes.ALOAD, slot);
							method.visitFieldInsn(Opcodes.PUTFIELD, node.name, field.name, field.desc);
						}
					}
				}

				slot += arg.getSize();
			}
		}

		private void result(MethodVisitor method, ClassNode owner, MethodNode original) {
			Type type = Type.getReturnType(original.desc);
			boolean factory = (original.access & Opcodes.ACC_STATIC) != 0;

			ClassNode resultClass = type.getSort() == Type.OBJECT ? classes.get(type.getClassName()) : null;

			if (type.getSort() == Type.OBJECT && (resultClass == null || (resultClass.access & Opcodes.ACC_INTERFACE) == 0)
					&& (type.getInternalName().equals(owner.name) || type.getInternalName().startsWith(owner.name + "$") || owner.name.startsWith(type.getInternalName() + "$"))) {
				if (!factory && type.getInternalName().equals(owner.name)) {
					method.visitVarInsn(Opcodes.ALOAD, 0);
				} else {
					method.visitTypeInsn(Opcodes.NEW, type.getInternalName());
					method.visitInsn(Opcodes.DUP);
					method.visitMethodInsn(Opcodes.INVOKESPECIAL, type.getInternalName(), "<init>", "()V", false);
				}

				method.visitInsn(Opcodes.ARETURN);
				return;
			}

			switch (type.getSort()) {
			case Type.VOID:
				method.visitInsn(Opcodes.RETURN);
				break;
			case Type.LONG:
				method.visitInsn(Opcodes.LCONST_0);
				method.visitInsn(Opcodes.LRETURN);
				break;
			case Type.FLOAT:
				method.visitInsn(Opcodes.FCONST_0);
				method.visitInsn(Opcodes.FRETURN);
				break;
			case Type.DOUBLE:
				method.visitInsn(Opcodes.DCONST_0);
				method.visitInsn(Opcodes.DRETURN);
				break;
			case Type.OBJECT:
			case Type.ARRAY:
				method.visitInsn(Opcodes.ACONST_NULL);
				method.visitInsn(Opcodes.ARETURN);
				break;
			default:
				method.visitInsn(Opcodes.ICONST_0);
				method.visitInsn(Opcodes.IRETURN);
				break;
			}
		}
	}
}
