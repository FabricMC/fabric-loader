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

package net.fabricmc.base.launch;

import net.fabricmc.base.Fabric;
import net.fabricmc.base.loader.Loader;
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

public class FabricClientTweaker implements ITweaker {

    private Map<String, String> args;

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
        Fabric.initialize(gameDir);
        Loader.load(new File(gameDir, "mods"));

        // Add Mixin tweaker
        ((List<String>) Launch.blackboard.get("TweakClasses")).add("org.spongepowered.asm.launch.MixinTweaker");

        // Setup Mixin environment
        MixinBootstrap.init();
        Mixins.addConfigurations(
                "fabricmc.mixins.client.json",
                "fabricmc.mixins.common.json");
        Loader.getClientMixinConfigs().forEach(Mixins::addConfiguration);
        Loader.getCommonMixinConfigs().forEach(Mixins::addConfiguration);
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
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
