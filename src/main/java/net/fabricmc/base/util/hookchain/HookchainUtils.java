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

package net.fabricmc.base.util.hookchain;

import net.fabricmc.api.Hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public final class HookchainUtils {
    private HookchainUtils() {

    }

    public static void addHook(IHookchain chain, Hook hook, MethodHandle callback) {
        synchronized (chain) {
            chain.add(hook.name(), callback);
            for (String s : hook.before()) {
                chain.addConstraint(s, hook.name());
            }
            for (String s : hook.after()) {
                chain.addConstraint(hook.name(), s);
            }
        }
    }

    public static void addAnnotatedHooks(IHookchain chain, Object o) {
        Class c = o.getClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (Method m : c.getMethods()) {
            if (m.isAnnotationPresent(Hook.class)) {
                try {
                    MethodHandle handle = lookup.unreflect(m).bindTo(o);
                    if (handle.type().equals(chain.getMethodType())) {
                        Hook hook = m.getAnnotation(Hook.class);
                        addHook(chain, hook, handle);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
