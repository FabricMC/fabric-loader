package net.fabricmc.loader.entrypoint.minecraft.hooks;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;

public class GuavaFix extends EntrypointPatch {

    public GuavaFix(EntrypointTransformer transformer) {
        super(transformer);
    }

    @Override
    public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
        try {
        	ClassNode node = loadClass(launcher, "net/minecraft/class_1084");
        	ClassNode node2 = loadClass(launcher, "net/minecraft/class_423");
            node.methods.forEach(method -> {
                ListIterator<AbstractInsnNode> instructions = method.instructions.iterator();
                while (instructions.hasNext()) {
                    AbstractInsnNode instruction = instructions.next();

                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.getOpcode() == Opcodes.INVOKESTATIC
                                && call.owner.equals("com/google/common/base/Objects")
                                && call.name.equals("firstNonNull")
                                && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                        	instructions.set(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/google/common/base/MoreObjects", "firstNonNull", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
                        }
                    }
                }
            });

            classEmitter.accept(node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}