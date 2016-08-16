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

import com.google.gson.GsonBuilder;
import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.net.Proxy;

public class FabricClientMain {

    public static void main(String[] args) {
        File file = new File(".gradle/minecraft/natives/");
        if (!file.exists()) {
            file = new File("../.gradle/minecraft/natives/");
        }
        System.setProperty("org.lwjgl.librarypath", file.getAbsolutePath());

        LaunchArguments arguments = new LaunchArguments(args);

        if (arguments.containsArgument("password")) {
            YggdrasilUserAuthentication auth = (YggdrasilUserAuthentication) (new YggdrasilAuthenticationService(Proxy.NO_PROXY, "1")).createUserAuthentication(Agent.MINECRAFT);
            auth.setUsername(arguments.getArgument("username"));
            auth.setPassword(arguments.getArgument("password"));
            arguments.removeArgument("password");

            try {
                auth.logIn();
            } catch (AuthenticationException e) {
                e.printStackTrace();
                return;
            }

            arguments.addArgument("accessToken", auth.getAuthenticatedToken());
            arguments.addArgument("uuid", auth.getSelectedProfile().getId().toString().replace("-", ""));
            arguments.addArgument("username", auth.getSelectedProfile().getName());
            arguments.addArgument("userType", auth.getUserType().getName());
            arguments.addArgument("userProperties", new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(auth.getUserProperties()));
        } else {
            arguments.addArgument("username", "Player");
        }

        arguments.addArgument("version", "16w32b");
        arguments.addArgument("assetIndex", "16w32b");
        arguments.addArgument("tweakClass", FabricClientTweaker.class.getName());

        if (!arguments.containsArgument("accessToken")) {
            arguments.addArgument("accessToken", "FabricMC");
        }

        Launch.main(arguments.getArguments());
    }

}
