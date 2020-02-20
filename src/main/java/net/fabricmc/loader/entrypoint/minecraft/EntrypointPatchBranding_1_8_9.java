package net.fabricmc.loader.entrypoint.minecraft;

import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchBranding_1_8_9 extends EntrypointPatch {

	public EntrypointPatchBranding_1_8_9(EntrypointTransformer transformer) {
		super(transformer);
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		loadClass(launcher, "net/minecraft/client/MinecraftClient").methods.forEach(m -> {
			String titleScreen = null;

			if (true) {
				ListIterator<AbstractInsnNode> instructions = m.instructions.iterator();

				while(instructions.hasNext()) {
					AbstractInsnNode node = instructions.next();

					if (node instanceof LdcInsnNode && "Post startup".equals(((LdcInsnNode) node).cst)) {
						while (instructions.hasNext()) {
							node = instructions.next();

							if (node instanceof MethodInsnNode) {
								MethodInsnNode invoke = (MethodInsnNode) node;

								if (invoke.getOpcode() == Opcodes.INVOKESPECIAL && invoke.name.equals("<init>") && invoke.desc.equals("()V")) {
									titleScreen = invoke.owner;
								}
							}
						}

						break;
					}
				}
			}
			if(titleScreen == null) {
				return;
			}
			ClassNode titleScreenClass = loadClass(launcher, titleScreen);
			titleScreenClass.methods.forEach(m2 -> {
				ListIterator<AbstractInsnNode> instructions2 = m2.instructions.iterator();

				while (instructions2.hasNext()) {
					AbstractInsnNode node = instructions2.next();

					if (node instanceof LdcInsnNode) {
						String constant = String.valueOf(((LdcInsnNode) node).cst);

						if (constant.startsWith("Minecraft ")) {
							instructions2.set(new LdcInsnNode(constant + "/Fabric"));
						}
					}
				}
			});

			classEmitter.accept(titleScreenClass);
		});
	}

	@Override
	protected ClassNode loadClass(FabricLauncher launcher, String className) {
		try {
			return super.loadClass(launcher, className);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}