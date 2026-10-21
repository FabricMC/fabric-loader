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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;

/**
 * Fixes networking of old game versions on Java 9+.
 *
 * <ul>
 * <li>The client entrypoint sets {@code java.net.preferIPv4Stack=true}, which makes the JDK's JNDI DNS client throw
 * <li>Netty 4.0 (up to 1.11) can't use direct buffers on Java 9+, but its epoll transport needs them and fails every
 * read with "Unable to access address of buffer". {@code Epoll.isAvailable()} is made to return {@code false} so the
 * game uses NIO.
 * </ul>
 */
public final class LegacyNetworkPatch extends GamePatch {
	private static final VersionPredicate PREFER_IPV4_VERSIONS = createVersionPredicate("<1.9");
	private static final VersionPredicate NETTY_4_0_VERSIONS = createVersionPredicate("<1.12");
	private static final String EPOLL = "io/netty/channel/epoll/Epoll";
	// Epoll itself is the same in 4.0 and 4.1, this sits next to it in every 4.1+ release
	private static final String NETTY_4_1_MARKER = "EpollRecvByteAllocatorHandle.class";

	private final MinecraftGameProvider gameProvider;
	private boolean patchEpoll;

	public LegacyNetworkPatch(MinecraftGameProvider gameProvider) {
		this.gameProvider = gameProvider;
	}

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
		String javaVersion = System.getProperty("java.specification.version");
		if (javaVersion.startsWith("1.")) return; // Java 8 or older

		Version gameVersion;

		try {
			gameVersion = Version.parse(gameProvider.getNormalizedGameVersion());
		} catch (VersionParsingException e) {
			Log.debug(LogCategory.GAME_PATCH, "Skipping legacy network patch, unparseable game version %s", gameProvider.getNormalizedGameVersion());
			return;
		}

		if (Integer.parseInt(javaVersion) >= 20 && PREFER_IPV4_VERSIONS.test(gameVersion)) {
			ClassNode mainClass = classSource.apply(gameProvider.getEntrypoint().replace('.', '/')); // internal name to reuse EntrypointPatch's result

			if (mainClass != null && removePreferIPv4Stack(mainClass)) {
				Log.debug(LogCategory.GAME_PATCH, "Removed java.net.preferIPv4Stack from %s", mainClass.name);
				classEmitter.accept(mainClass);
			}
		}

		// Done at load time since a launcher or mod may provide a newer Netty, which works fine
		patchEpoll = NETTY_4_0_VERSIONS.test(gameVersion);
	}

	@Override
	public Collection<String> getLoadTimeTargets() {
		return patchEpoll ? Collections.singletonList(EPOLL) : Collections.emptyList();
	}

	@Override
	public boolean processLoaded(FabricLauncher launcher, ClassNode classNode) {
		// Only the copy of Netty that provides Epoll matters, so look for the marker next to it
		URL epollUrl = launcher.getTargetClassLoader().getResource(EPOLL + ".class");
		if (epollUrl != null && isNetty41(epollUrl)) return false;

		if (!disableEpoll(classNode)) return false;

		Log.debug(LogCategory.GAME_PATCH, "Disabled netty epoll transport");
		return true;
	}

	static boolean isNetty41(URL epollUrl) {
		try (InputStream is = new URL(epollUrl, NETTY_4_1_MARKER).openStream()) {
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			// NIO works with any Netty, so disabling epoll is the safe fallback
			Log.warn(LogCategory.GAME_PATCH, "Failed to check netty version at %s, disabling epoll", epollUrl, e);
			return false;
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
		for (MethodNode method : classNode.methods) {
			if (method.name.equals("isAvailable") && method.desc.equals("()Z")) {
				InsnList insns = new InsnList();
				insns.add(new InsnNode(Opcodes.ICONST_0));
				insns.add(new InsnNode(Opcodes.IRETURN));

				method.instructions = insns;
				method.tryCatchBlocks.clear();
				method.localVariables = null;
				method.maxStack = 1;
				method.maxLocals = 0;
				return true;
			}
		}

		return false;
	}

	private static VersionPredicate createVersionPredicate(String predicate) {
		try {
			return VersionPredicateParser.parse(predicate);
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}
}
