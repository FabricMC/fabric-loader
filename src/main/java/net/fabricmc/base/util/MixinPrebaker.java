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

package net.fabricmc.base.util;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import net.fabricmc.base.loader.MixinLoader;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.Blackboard;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * The purpose of this class is to provide an utility for baking mixins from
 * mods into a JAR file at compile time to make accessing APIs provided by them
 * more intuitive in development environment.
 */
public class MixinPrebaker {
    public static final String SESSION_ID_FILENAME = ".fabric-baked-mixin-session";

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("usage: MixinPrebaker <input-jar> <output-jar> <mod-jars...>");
            return;
        }

        Set<File> modFiles = new HashSet<>();
        for (int i = 2; i < args.length; i++) {
            modFiles.add(new File(args[i]));
        }

        URLClassLoader ucl = (URLClassLoader) MixinPrebaker.class.getClassLoader();
        Launch.classLoader = new LaunchClassLoader(ucl.getURLs());
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put(Blackboard.Keys.TWEAKS, Collections.emptyList());

        MixinLoader mixinLoader = new MixinLoader();
        mixinLoader.load(modFiles);

        MixinBootstrap.init();
        Mixins.addConfigurations("fabricmc.mixins.common.json",
                "fabricmc.mixins.client.json",
                "fabricmc.mixins.server.json");
        mixinLoader.getCommonMixinConfigs().forEach(Mixins::addConfiguration);
        mixinLoader.getClientMixinConfigs().forEach(Mixins::addConfiguration);
        mixinLoader.getServerMixinConfigs().forEach(Mixins::addConfiguration);

        MixinEnvironment.EnvironmentStateTweaker tweaker = new MixinEnvironment.EnvironmentStateTweaker();
        tweaker.getLaunchArguments();
        tweaker.injectIntoClassLoader(Launch.classLoader);

        MixinTransformer mixinTransformer = Blackboard.get(Blackboard.Keys.TRANSFORMER);

        try {
            JarInputStream input = new JarInputStream(new FileInputStream(new File(args[0])));
            JarOutputStream output = new JarOutputStream(new FileOutputStream(new File(args[1])));
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                if (entry.getName().equals(SESSION_ID_FILENAME)) {
                    continue;
                }

                if (entry.getName().endsWith(".class")) {
                    byte[] classIn = ByteStreams.toByteArray(input);
                    String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                    byte[] classOut = mixinTransformer.transform(className, className, classIn);
                    if (classIn != classOut) {
                        System.out.println("Transformed " + className);
                    }
                    JarEntry newEntry = new JarEntry(entry.getName());
                    newEntry.setComment(entry.getComment());
                    newEntry.setSize(classOut.length);
                    newEntry.setLastModifiedTime(FileTime.from(Instant.now()));
                    output.putNextEntry(newEntry);
                    output.write(classOut);
                } else {
                    output.putNextEntry(entry);
                    ByteStreams.copy(input, output);
                }
            }

            output.putNextEntry(new JarEntry(SESSION_ID_FILENAME));
            output.write("todo".getBytes(Charsets.UTF_8));

            input.close();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
