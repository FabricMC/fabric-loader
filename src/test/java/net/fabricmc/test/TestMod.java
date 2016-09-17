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

package net.fabricmc.test;

import net.fabricmc.base.loader.Init;
import net.fabricmc.base.loader.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestMod {

    private static final Logger LOGGER = LogManager.getFormatterLogger("TestMod");

    @Init
    public void init() {
        LOGGER.info("**************************");
        LOGGER.info("Hello from Fabric");
        LOGGER.info("**************************");

        Loader.INSTANCE.modsInitialized.subscribe(this::onModsInitialized);
    }

    public void onModsInitialized() {
        System.out.println("Mods initialized");
    }

}
