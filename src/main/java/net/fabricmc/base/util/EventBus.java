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

import net.fabricmc.api.Event;
import net.fabricmc.api.Hook;
import net.fabricmc.base.util.hookchain.HookchainUtils;
import net.fabricmc.base.util.hookchain.IHookchain;
import net.fabricmc.base.util.hookchain.OrderedHookchain;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class EventBus {
    private final Map<Class<? extends Event>, IHookchain> eventChains;

    public EventBus() {
        this.eventChains = new HashMap<>();
    }

    private MethodType getMethodType(Class c) {
        return MethodType.methodType(void.class, c);
    }

    private IHookchain getChain(Class c) {
        return eventChains.get(c);
    }

    private IHookchain getOrCreateChain(Class c) {
        if (eventChains.containsKey(c)) {
            return eventChains.get(c);
        } else {
            IHookchain ret = new OrderedHookchain(getMethodType(c));
            eventChains.put(c, ret);
            return ret;
        }
    }

    public void register(Object o) {
        Class c = o.getClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (Method m : c.getMethods()) {
            if (m.isAnnotationPresent(Hook.class)
                    && m.getParameterCount() == 1) {
                try {
                    Class parameterClass = m.getParameterTypes()[0];
                    if (parameterClass != Event.class && Event.class.isAssignableFrom(parameterClass)) {
                        IHookchain chain = getOrCreateChain(parameterClass);
                        Hook hook = m.getAnnotation(Hook.class);
                        MethodHandle handle = lookup.unreflect(m).bindTo(o);
                        HookchainUtils.addHook(chain, hook, handle);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void call(Object o) {
        IHookchain chain = getChain(o.getClass());
        if (chain != null) {
            chain.call(o);
        }
    }
}
