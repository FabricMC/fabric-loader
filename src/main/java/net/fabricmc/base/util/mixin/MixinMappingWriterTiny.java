package net.fabricmc.base.util.mixin;

import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.ObfuscationType;
import org.spongepowered.tools.obfuscation.mapping.IMappingConsumer;
import org.spongepowered.tools.obfuscation.mapping.common.MappingWriter;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by asie on 10/9/16.
 */
public class MixinMappingWriterTiny extends MappingWriter {
    private final String from, to;

    public MixinMappingWriterTiny(Messager messager, Filer filer, String from, String to) {
        super(messager, filer);
        this.from = from;
        this.to = to;
    }

    @Override
    public void write(String output, ObfuscationType type, IMappingConsumer.MappingSet<MappingField> fields, IMappingConsumer.MappingSet<MappingMethod> methods) {
        if (output != null) {
            PrintWriter writer = null;

            try {
                writer = this.openFileWriter(output, type + " output TinyMappings");
                writer.println(String.format("v1\t%s\t%s", from, to));
                for (IMappingConsumer.MappingSet.Pair<MappingField> pair : fields) {
                    writer.println(String.format("FIELD\t%s\t%s\t%s\t%s", pair.from.getOwner(), pair.from.getDesc(), pair.from.getName(), pair.to.getName()));
                }
                for (IMappingConsumer.MappingSet.Pair<MappingMethod> pair : methods) {
                    writer.println(String.format("METHOD\t%s\t%s\t%s\t%s", pair.from.getOwner(), pair.from.getDesc(), pair.from.getName(), pair.to.getName()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
}
