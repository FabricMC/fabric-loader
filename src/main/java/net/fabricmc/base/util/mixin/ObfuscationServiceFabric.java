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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.tools.obfuscation.service.IObfuscationService;
import org.spongepowered.tools.obfuscation.service.ObfuscationTypeDescriptor;

import java.util.Collection;
import java.util.Set;

public class ObfuscationServiceFabric implements IObfuscationService {
    public static final String IN_MAP_FILE          = "inMapFile";
    public static final String IN_MAP_EXTRA_FILES          = "inMapExtraFiles";
    public static final String OUT_MAP_FILE        = "outMapFile";

    private String asSuffixed(String arg, String from, String to) {
        return arg + StringUtils.capitalize(from) + StringUtils.capitalize(to);
    }

    private ObfuscationTypeDescriptor createObfuscationType(String from, String to) {
        return new ObfuscationTypeDescriptor(
                from + ":" + to,
                asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to),
                asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to),
                asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to),
                ObfuscationEnvironmentFabric.class
        );
    }

    private void addSupportedOptions(ImmutableSet.Builder builder, String from, String to) {
        builder.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to));
        builder.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to));
        builder.add(asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to));
    }

    @Override
    public Set<String> getSupportedOptions() {
        ImmutableSet.Builder builder = new ImmutableSet.Builder();
        addSupportedOptions(builder, "mojang", "pomf");
        addSupportedOptions(builder, "pomf", "mojang");
        return builder.build();
    }

    @Override
    public Collection<ObfuscationTypeDescriptor> getObfuscationTypes() {
        return ImmutableSet.of(
                createObfuscationType("mojang", "pomf"),
                createObfuscationType("pomf", "mojang")
        );
    }
}
