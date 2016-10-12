package net.fabricmc.base.util.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.tools.obfuscation.ObfuscationEnvironment;
import org.spongepowered.tools.obfuscation.mcp.ObfuscationServiceMCP;
import org.spongepowered.tools.obfuscation.service.IObfuscationService;
import org.spongepowered.tools.obfuscation.service.ObfuscationTypeDescriptor;

import java.util.Collection;
import java.util.Set;

/**
 * Created by asie on 10/12/16.
 */
public class ObfuscationServiceFabric implements IObfuscationService {
    public static final String IN_MAP_FILE          = "inMapFile";
    public static final String IN_MAP_EXTRA_FILES          = "inMapExtraFiles";
    public static final String OUT_MAP_FILE        = "outMapFile";
    public static final String OUT_REFMAP_FILE         = "outRefMapFile";

    private Collection<ObfuscationTypeDescriptor> createObfuscationTypes(String... keys) {
        ImmutableSet.Builder builder = ImmutableSet.builder();
        for (String key : keys) {
            builder.add(new ObfuscationTypeDescriptor(
                    key,
                    ObfuscationServiceFabric.IN_MAP_FILE,
                    ObfuscationServiceFabric.IN_MAP_EXTRA_FILES,
                    ObfuscationServiceFabric.OUT_MAP_FILE,
                    ObfuscationEnvironmentFabric.class
            ));
        }
        return builder.build();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return ImmutableSet.of(IN_MAP_FILE, OUT_MAP_FILE, OUT_REFMAP_FILE);
    }

    @Override
    public Collection<ObfuscationTypeDescriptor> getObfuscationTypes() {
        return createObfuscationTypes("mojang:pomf", "pomf:mojang");
    }
}
