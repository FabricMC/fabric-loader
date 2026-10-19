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

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/**
 * Fixes networking of old game versions on Java 9+.
 *
 * <ul>
 * <li>The client entrypoint sets {@code java.net.preferIPv4Stack=true}, which makes the JDK's JNDI DNS client throw
 * {@code UnsupportedAddressTypeException} when the first system DNS server is IPv6 (Java 9+). The game swallows it and
 * skips the SRV lookup, so e.g. {@code hypixel.net} connects to the website instead and times out. Newer versions no
 * longer set the property, so the call is removed.
 * <li>Netty 4.0 (up to 1.11) can't use direct buffers on Java 9+, but its epoll transport needs them and fails every
 * read with "Unable to access address of buffer". {@code Epoll.isAvailable()} is replaced with {@code false} so the
 * game uses NIO.
 * </ul>
 */
public final class LegacyNetworkPatch extends GamePatch {
	private static final String EPOLL = "io/netty/channel/epoll/Epoll";
	private static final byte[] EPOLL_MARKER = EPOLL.getBytes(StandardCharsets.UTF_8);

	private final MinecraftGameProvider gameProvider;

	public LegacyNetworkPatch(MinecraftGameProvider gameProvider) {
		this.gameProvider = gameProvider;
	}

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
		if (System.getProperty("java.specification.version").startsWith("1.")) return; // Java 8 or older

		ClassNode mainClass = classSource.apply(gameProvider.getEntrypoint().replace('.', '/')); // internal name to reuse EntrypointPatch's result

		if (mainClass != null && removePreferIPv4Stack(mainClass)) {
			Log.debug(LogCategory.GAME_PATCH, "Removed java.net.preferIPv4Stack from %s", mainClass.name);
			classEmitter.accept(mainClass);
		}

		if (ClassLoader.getSystemResource(EPOLL + ".class") == null
				|| ClassLoader.getSystemResource("io/netty/util/internal/ReflectionUtil.class") != null) {
			return;
		}

		for (String name : findEpollUsers(gameProvider.getGameJar())) {
			ClassNode classNode = classSource.apply(name);

			if (classNode != null && disableEpoll(classNode)) {
				Log.debug(LogCategory.GAME_PATCH, "Disabled netty epoll transport in %s", classNode.name);
				classEmitter.accept(classNode);
			}
		}
	}

	private static boolean removePreferIPv4Stack(ClassNode classNode) {
		boolean patched = false;

		for (MethodNode method : classNode.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof LdcInsnNode && "java.net.preferIPv4Stack".equals(((LdcInsnNode) insn).cst)) {
					AbstractInsnNode value = insn.getNext();
					AbstractInsnNode call = value != null ? value.getNext() : null;
					AbstractInsnNode pop = call != null ? call.getNext() : null;

					if (value instanceof LdcInsnNode
							&& call instanceof MethodInsnNode
							&& ((MethodInsnNode) call).owner.equals("java/lang/System")
							&& ((MethodInsnNode) call).name.equals("setProperty")
							&& pop != null && pop.getOpcode() == Opcodes.POP) {
						method.instructions.remove(insn);
						method.instructions.remove(value);
						method.instructions.remove(call);
						method.instructions.remove(pop);
						patched = true;
					}
				}
			}
		}

		return patched;
	}

	private static boolean disableEpoll(ClassNode classNode) {
		boolean patched = false;

		for (MethodNode method : classNode.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn.getOpcode() == Opcodes.INVOKESTATIC
						&& ((MethodInsnNode) insn).owner.equals(EPOLL)
						&& ((MethodInsnNode) insn).name.equals("isAvailable")) {
					method.instructions.set(insn, new InsnNode(Opcodes.ICONST_0));
					patched = true;
				}
			}
		}

		return patched;
	}

	private static List<String> findEpollUsers(Path gameJar) {
		List<String> ret = new ArrayList<>();
		if (!Files.isRegularFile(gameJar)) return ret;

		try (ZipFile zf = new ZipFile(gameJar.toFile())) {
			for (Enumeration<? extends ZipEntry> it = zf.entries(); it.hasMoreElements(); ) {
				ZipEntry entry = it.nextElement();
				String name = entry.getName();

				if (!name.endsWith(".class") || name.startsWith("io/netty/")) continue;

				byte[] data = new byte[(int) entry.getSize()];

				try (DataInputStream is = new DataInputStream(zf.getInputStream(entry))) {
					is.readFully(data);
				}

				if (contains(data, EPOLL_MARKER)) {
					ret.add(name.substring(0, name.length() - ".class".length()));
				}
			}
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
		}

		return ret;
	}

	private static boolean contains(byte[] data, byte[] needle) {
		outer: for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) continue outer;
			}

			return true;
		}

		return false;
	}
}
