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

import net.fabricmc.base.util.hookchain.HookchainUtils;
import net.fabricmc.base.util.hookchain.IFlexibleHookchain;
import net.fabricmc.base.util.hookchain.TreeHookchain;

import java.lang.invoke.MethodType;

/**
 * Created by asie on 8/19/16.
 */
public class LoadingBus {
    private final IFlexibleHookchain hookchain;

    public LoadingBus() {
        hookchain = new TreeHookchain(MethodType.methodType(void.class));
    }

    public void register(Object o) {
        HookchainUtils.addAnnotatedHooks(hookchain, o);
    }

    public void registerHookName(String mark) {
        hookchain.add(mark);
    }

    public void start() {
        hookchain.call();
    }

    public void call(String mark) {
        hookchain.call(mark);
    }
}
