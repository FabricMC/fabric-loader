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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.loader.impl.game.minecraft.menu.MenuMappings;
import net.fabricmc.loader.impl.game.minecraft.menu.MenuRuntimeCheck;

public final class MenuClientCheck {
	private MenuClientCheck() {
	}

	public static void main(String[] args) throws IOException {
		Path directory = Paths.get(args[0]);
		List<Path> clients;

		try (Stream<Path> paths = Files.list(directory)) {
			clients = paths.filter(p -> Files.isRegularFile(p.resolve("complete.json"))).sorted().collect(Collectors.toList());
		}

		int failures = 0;

		if (args.length > 1 && "--all".equals(args[1])) {
			try (java.io.Reader reader = Files.newBufferedReader(directory.resolve("catalog.json"))) {
				for (JsonElement game : new JsonParser().parse(reader).getAsJsonArray()) {
					String version = game.getAsJsonObject().get("version").getAsString();

					if (!Files.isRegularFile(directory.resolve(version).resolve("complete.json"))) {
						System.out.println("MISSING " + version);
						failures++;
					}
				}
			}
		}

		for (Path client : clients) {
			try {
				check(client);
				System.out.println("PASS " + client.getFileName());
			} catch (RuntimeException | IOException | ReflectiveOperationException | LinkageError e) {
				failures++;
				Throwable cause = e;

				while (cause.getCause() != null) {
					cause = cause.getCause();
				}

				System.out.println("FAIL " + client.getFileName() + " " + cause);
			}
		}

		System.out.println("Checked " + clients.size() + " clients; " + failures + " failures");
		if (clients.isEmpty() || failures != 0) throw new IllegalStateException("Minecraft menu compatibility check failed");
	}

	static void check(Path client) throws IOException, ReflectiveOperationException {
		Map<String, ClassNode> original = new HashMap<>();

		try (Stream<Path> paths = Files.walk(client.resolve("classes"))) {
			for (Path path : paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
				ClassNode node = new ClassNode();
				new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				original.put(node.name, node);
			}
		}

		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(client.resolve("mappings.tiny"), tree);
		int namespace = tree.getNamespaceId("intermediary");
		Remapper remapper = new Remapper(Opcodes.ASM9) {
			@Override
			public String map(String name) {
				MappingTreeView.ClassMappingView mapping = tree.getClass(name);
				String mapped = mapping == null ? null : mapping.getName(namespace);
				return mapped == null ? name : mapped;
			}

			@Override
			public String mapMethodName(String owner, String name, String descriptor) {
				return member(owner, name, descriptor, false);
			}

			@Override
			public String mapFieldName(String owner, String name, String descriptor) {
				return member(owner, name, descriptor, true);
			}

			private String member(String owner, String name, String descriptor, boolean field) {
				String mapped = find(owner, name, descriptor, field, new ArrayList<>());
				return mapped == null ? name : mapped;
			}

			private String find(String owner, String name, String descriptor, boolean field, List<String> visited) {
				if (owner == null || visited.contains(owner)) return null;
				visited.add(owner);
				MappingTreeView.ClassMappingView mapping = tree.getClass(owner);

				if (mapping != null) {
					MappingTreeView.MemberMappingView member = field ? mapping.getField(name, descriptor) : mapping.getMethod(name, descriptor);
					if (member != null && member.getName(namespace) != null) return member.getName(namespace);
				}

				ClassNode type = original.get(owner);
				if (type == null) return null;
				String result = find(type.superName, name, descriptor, field, visited);
				if (result != null) return result;

				for (String iface : type.interfaces) {
					result = find(iface, name, descriptor, field, visited);
					if (result != null) return result;
				}

				return null;
			}
		};
		Map<String, ClassNode> classes = new HashMap<>();

		for (ClassNode node : original.values()) {
			ClassNode mapped = new ClassNode();
			node.accept(new ClassRemapper(mapped, remapper));
			classes.put(mapped.name.replace('/', '.'), mapped);
		}

		Map<String, ClassNode> generated = new HashMap<>();
		ModsMenuPatch.apply(classes::get, node -> generated.put(node.name, node), new MenuMappings(null));

		for (ClassNode node : generated.values()) {
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			new ClassReader(writer.toByteArray());
		}

		ClassNode screen = classes.get(generated.get(ModsMenuPatch.SCREEN).superName.replace('/', '.'));
		checkField(classes, screen, "I", "field_22789", "field_2561", "width");
		checkField(classes, screen, "I", "field_22790", "field_2559", "height");
		ClassNode button = classes.get("net.minecraft.class_4185");
		if (button == null) button = classes.get("net.minecraft.client.gui.components.Button");
		if (button == null) button = classes.get("net.minecraft.class_339");
		checkField(classes, button, "Z", "field_22763", "field_2078", "active", "enabled");
		checkMethod(screen, "method_37063", "method_25411", "method_2219", "addDrawableChild", "addRenderableWidget", "addButton");
		ClassNode minecraft = classes.get("net.minecraft.class_310");
		if (minecraft == null) minecraft = classes.get("net.minecraft.client.Minecraft");
		checkMethod(minecraft, "method_1507", "setScreen", "setScreenAndShow", "openScreen");
		checkMethod(minecraft, "method_1551", "getInstance");

		if (!generated.containsKey(ModsMenuPatch.BUTTON)) {
			boolean constructor = button.methods.stream().anyMatch(m -> "<init>".equals(m.name) && Type.getArgumentTypes(m.desc).length == 6);

			if (!constructor) {
				MethodNode builder = checkMethod(button, "method_46430", "builder");
				ClassNode builderClass = classes.get(Type.getReturnType(builder.desc).getClassName());
				checkMethod(builderClass, "method_46434", "dimensions", "bounds");
				checkMethod(builderClass, "method_46431", "build");
			}
		}

		MenuRuntimeCheck.check(classes, generated);
	}

	private static MethodNode checkMethod(ClassNode owner, String... names) {
		if (owner != null) {
			for (String name : names) {
				for (MethodNode method : owner.methods) {
					if (method.name.equals(name)) return method;
				}
			}
		}

		throw new IllegalArgumentException("Missing method " + String.join("/", names) + " in " + (owner == null ? "missing class" : owner.name));
	}

	private static void checkField(Map<String, ClassNode> classes, ClassNode owner, String descriptor, String... names) {
		for (ClassNode type = owner; type != null; type = classes.get(type.superName.replace('/', '.'))) {
			for (String name : names) {
				if (type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))) return;
			}
		}

		throw new IllegalArgumentException("Missing field " + String.join("/", names));
	}
}
