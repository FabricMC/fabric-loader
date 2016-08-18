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

package net.fabricmc.base.loader;

import net.fabricmc.base.loader.language.ILanguageAdapter;

public class ModContainer {

    private ModInfo info;
    private ILanguageAdapter adapter;
    private Object instance;

    public ModContainer(ModInfo info) {
        this.info = info;
        if (!info.getModClass().isEmpty()) {
            this.adapter = createAdapter();
            this.instance = createInstance();
        }
    }

    public void initialize() {
        adapter.callInitializationMethods(instance);
    }

    public boolean hasInstance() {
        return instance != null;
    }

    public ModInfo getInfo() {
        return info;
    }

    public ILanguageAdapter getAdapter() {
        return adapter;
    }

    public Object getInstance() {
        return instance;
    }

    private ILanguageAdapter createAdapter() {
        try {
            return (ILanguageAdapter)Class.forName(info.getLanguageAdapter()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create language adapter %s for mod %s.%s", info.getLanguageAdapter(), info.getGroup(), info.getId()), e);
        }
    }

    private Object createInstance() {
        try {
            return adapter.createModInstance(Class.forName(info.getModClass()));
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create mod instance for mod %s.%s", info.getGroup(), info.getId()), e);
        }
    }
}
