package net.fabricmc.base.launch;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import net.fabricmc.api.Side;
import net.fabricmc.base.loader.MixinLoader;
import net.fabricmc.base.util.mixin.MixinDevRemapper;
import net.fabricmc.base.util.mixin.MixinPrebaker;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FabricMixinBootstrap {
    public static final String APPLIED_MIXIN_CONFIGS_FILENAME = ".fabric-applied-mixin-configs";
    public static final String MAPPINGS_FILENAME = ".fabric-dev-mappings.tiny";
    private static List<String> appliedMixinConfigs;
    private static boolean initialized = false;
    private static File mappingFile;

    public static File getMappingFile() {
        return mappingFile;
    }

    public static void setMappingFile(File value) {
        mappingFile = value;
    }

    public static void addConfiguration(String configuration) {
        if (appliedMixinConfigs == null || !appliedMixinConfigs.contains(configuration)) {
            Mixins.addConfiguration(configuration);
        }
    }

    public static void init(Side side, MixinLoader mixinLoader) {
        if (initialized) {
            throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
        }

        InputStream appliedMixinsStream = MixinPrebaker.class.getClassLoader().getResourceAsStream(APPLIED_MIXIN_CONFIGS_FILENAME);
        if (appliedMixinsStream != null) {
            try {
                byte[] data = ByteStreams.toByteArray(appliedMixinsStream);
                appliedMixinConfigs = Arrays.asList(new String(data, Charsets.UTF_8).split("\n"));
                appliedMixinsStream.close();
            } catch (IOException e) {
                System.err.println("Fabric development environment setup error - the game will probably crash soon!");
                e.printStackTrace();
            }
        }

        try {
            InputStream mappingStream = mappingFile != null
                    ? new FileInputStream(mappingFile)
                    : MixinPrebaker.class.getClassLoader().getResourceAsStream(MAPPINGS_FILENAME);

            if (mappingStream != null) {
                try {
                    MixinDevRemapper remapper = new MixinDevRemapper();
                    remapper.readMapping(new BufferedReader(new InputStreamReader(mappingStream)), "mojang", "pomf");
                    mappingStream.close();

                    MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
                } catch (IOException e) {
                    System.err.println("Fabric development environment setup error - the game will probably crash soon!");
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e) {
            // Ignore
        }

        MixinBootstrap.init();

        addConfiguration("fabricmc.mixins.common.json");
        if (side.hasClient()) {
            addConfiguration("fabricmc.mixins.client.json");
        }
        if (side.hasServer()) {
            addConfiguration("fabricmc.mixins.server.json");
        }

        mixinLoader.getCommonMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
        if (side.hasClient()) {
            mixinLoader.getClientMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
        }
        if (side.hasServer()) {
            mixinLoader.getServerMixinConfigs().forEach(FabricMixinBootstrap::addConfiguration);
        }

        initialized = true;
    }

    public static List<String> getAppliedMixinConfigs() {
        return appliedMixinConfigs;
    }
}
