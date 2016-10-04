package net.fabricmc.base.launch;

import net.fabricmc.base.loader.MixinLoader;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class FabricTweaker implements ITweaker {
    protected Map<String, String> args;
    protected MixinLoader mixinLoader;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.args = (Map<String, String>) Launch.blackboard.get("launchArgs");

        if (this.args == null) {
            this.args = new HashMap<>();
            Launch.blackboard.put("launchArgs", this.args);
        }

        if (!this.args.containsKey("--version")) {
            this.args.put("--version", profile != null ? profile : "Fabric");
        }

        if (!this.args.containsKey("--gameDir")) {
            if (gameDir == null) gameDir = new File(".");
            this.args.put("--gameDir", gameDir.getAbsolutePath());
        }

        if (!this.args.containsKey("--assetsDir") && assetsDir != null) {
            this.args.put("--assetsDir", assetsDir.getAbsolutePath());
        }
        if (!this.args.containsKey("--accessToken")) {
            this.args.put("--accessToken", "FabricMC");
        }
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--")) {
                this.args.put(arg, args.get(i + 1));
            }
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
        File gameDir = new File(args.get("--gameDir"));
        mixinLoader = new MixinLoader();
        mixinLoader.load(new File(gameDir, "mods"));

        // Setup Mixin environment
        MixinBootstrap.init();
        Mixins.addConfigurations("fabricmc.mixins.common.json");
        mixinLoader.getCommonMixinConfigs().forEach(Mixins::addConfiguration);
    }

    @Override
    public String[] getLaunchArguments() {
        List<String> launchArgs = new ArrayList<>();
        for (Map.Entry<String, String> arg : this.args.entrySet()) {
            launchArgs.add(arg.getKey());
            launchArgs.add(arg.getValue());
        }
        return launchArgs.toArray(new String[launchArgs.size()]);
    }
}
