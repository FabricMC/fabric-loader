package net.fabricmc.base.util.mixin;

import org.spongepowered.tools.obfuscation.ObfuscationEnvironment;
import org.spongepowered.tools.obfuscation.ObfuscationType;
import org.spongepowered.tools.obfuscation.mapping.IMappingProvider;
import org.spongepowered.tools.obfuscation.mapping.IMappingWriter;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;

public class ObfuscationEnvironmentFabric extends ObfuscationEnvironment {
    private final String from, to;

    protected ObfuscationEnvironmentFabric(ObfuscationType type) {
        super(type);
        from = type.getKey().split(":")[0];
        to = type.getKey().split(":")[1];
    }

    @Override
    protected IMappingProvider getMappingProvider(Messager messager, Filer filer) {
        return new MixinMappingProviderTiny(messager, filer, from, to);
    }

    @Override
    protected IMappingWriter getMappingWriter(Messager messager, Filer filer) {
        return new MixinMappingWriterTiny(messager, filer, from, to);
    }
}
