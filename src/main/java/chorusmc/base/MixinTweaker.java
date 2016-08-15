package chorusmc.base;

import net.minecraft.client.main.Main;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixinTweaker implements ITweaker {

    private Map<String, String> args;

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.args = (Map<String, String>) Launch.blackboard.get("launchArgs");
        ((List<String>) Launch.blackboard.get("TweakClasses")).add("org.spongepowered.asm.launch.MixinTweaker");
        if (this.args == null) {
            this.args = new HashMap<>();
            Launch.blackboard.put("launchArgs", this.args);
        }
        if (!this.args.containsKey("--version")) {
            this.args.put("--version", profile != null ? profile : "OML");
        }
        if (!this.args.containsKey("--gameDir") && gameDir != null) {
            this.args.put("--gameDir", gameDir.getAbsolutePath());
        }
        if (!this.args.containsKey("--assetsDir") && assetsDir != null) {
            this.args.put("--assetsDir", assetsDir.getAbsolutePath());
        }
        if (!this.args.containsKey("--accessToken")) {
            this.args.put("--accessToken", "ChorusMC");
        }
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--")) {
                this.args.put(arg, args.get(i + 1));
            }
        }
        MixinBootstrap.init();
        Mixins.addConfiguration("chorusmc.mixins.json");
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
        System.out.println("hello");
    }

    @Override
    public String getLaunchTarget() {
        return Main.class.getCanonicalName();
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
