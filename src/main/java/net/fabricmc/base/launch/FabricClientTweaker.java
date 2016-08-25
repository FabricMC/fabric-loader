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
import net.fabricmc.base.loader.MixinLoader;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
        
        createEarlyDisplay();
    }

    private void createEarlyDisplay() {
        File gameDir = new File(args.get("--gameDir"));
        Properties def = new Properties();
        def.setProperty("fullscreen", "false");
        def.setProperty("overrideWidth", "0");
        def.setProperty("overrideHeight", "0");
        Properties props = new Properties(def);
        try {
            props.load(Files.newReader(new File(gameDir, "options.txt"), StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean fullscreen = args.containsKey("--fullscreen") || Boolean.parseBoolean(props.getProperty("fullscreen"));
        int overrideWidth = Integer.parseInt(props.getProperty("overrideWidth"));
        int overrideHeight = Integer.parseInt(props.getProperty("overrideHeight"));
        int width = 854;
        int height = 480;
        if (overrideWidth > 0) {
            width = overrideWidth;
        } else if (args.containsKey("--width")) {
            width = Math.max(1, Integer.parseInt(args.get("--width")));
        }
        if (overrideHeight > 0) {
            height = overrideHeight;
        } else if (args.containsKey("--height")) {
            height = Math.max(1, Integer.parseInt(args.get("--height")));
        }
        String ver = args.get("--version");
        if (ver == null) {
            ver = "Unknown";
        } else {
            int index = ver.indexOf('-');
            if (index != -1) {
                ver = ver.substring(0, index);
            }
        }
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            Display.setFullscreen(fullscreen);
            Display.setResizable(true);
            Display.setTitle("Minecraft "+ver);
            try {
                Display.create(new PixelFormat().withDepthBits(24));
            } catch (LWJGLException e) {
                e.printStackTrace();
                Display.create();
            }
            GL11.glClearColor(1, 1, 1, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            Display.update();
        } catch (LWJGLException e1) {
            e1.printStackTrace();
            Throwables.propagate(e1);
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
        File gameDir = new File(args.get("--gameDir"));
        MixinLoader loader = new MixinLoader();
        loader.load(new File(gameDir, "mods"));

        // Setup Mixin environment
        MixinBootstrap.init();
        Mixins.addConfigurations(
                "fabricmc.mixins.client.json",
                "fabricmc.mixins.common.json");
        loader.getClientMixinConfigs().forEach(Mixins::addConfiguration);
        loader.getCommonMixinConfigs().forEach(Mixins::addConfiguration);
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);

        // Run the first pass of the loading bus before Minecraft loading begins
        Fabric.getLoadingBus().start();
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
