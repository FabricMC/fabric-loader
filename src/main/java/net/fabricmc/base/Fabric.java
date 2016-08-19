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

package net.fabricmc.base;

import net.fabricmc.base.util.EventBus;
import net.fabricmc.base.util.LoadingBus;
import net.fabricmc.base.util.hookchain.IFlexibleHookchain;
import net.fabricmc.base.util.hookchain.TreeHookchain;

import java.io.File;
import java.lang.invoke.MethodType;

public final class Fabric {
    private static final EventBus EVENT_BUS;
    private static final LoadingBus LOADING_BUS;

    private static boolean initialized = false;

    private static File gameDir;
    private static File configDir;

    // INTERNAL: DO NOT USE
    public static void initialize(File gameDir) {
        if (initialized) {
            throw new RuntimeException("Fabric has already been initialized");
        }

        Fabric.gameDir = gameDir;
        initialized = true;
    }

    public static File getGameDirectory() {
        return gameDir;
    }

    public static File getConfigDirectory() {
        if (configDir == null) {
            configDir = new File(gameDir, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
        }
        return configDir;
    }

    public static EventBus getEventBus() {
        return EVENT_BUS;
    }

    public static LoadingBus getLoadingBus() {
        return LOADING_BUS;
    }

    private Fabric() {}

    static {
        EVENT_BUS = new EventBus();
        LOADING_BUS = new LoadingBus();
    }
}
