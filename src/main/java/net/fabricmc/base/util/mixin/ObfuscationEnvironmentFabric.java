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
