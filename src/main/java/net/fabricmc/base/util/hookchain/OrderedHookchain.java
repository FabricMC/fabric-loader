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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Ordered hookchain implementation.
 *
 * @author greaser
 */
public class OrderedHookchain extends Hookchain<OrderedHookchain.HookData> {
    /**
     * Central data structure for the ordered hookchain.
     */
    protected class HookData {
        public final String name;
        public MethodHandle callback;
        private final Set<String> hooksBefore = new HashSet<String>();
        private final Set<String> hooksAfter = new HashSet<String>();

        public HookData(String name, MethodHandle callback) {
            assert (name != null);
            assert (callback != null);

            this.name = name;
            this.callback = callback;
        }

        public void addHookBefore(String oname) {
            assert (oname != null);
            this.hooksAfter.add(oname);
        }

        public void addHookAfter(String oname) {
            assert (oname != null);
            this.hooksBefore.add(oname);
        }

        public boolean areDependenciesSatisfied(Set<String> dependenciesProvided) {
            assert (dependenciesProvided != null);
            return dependenciesProvided.containsAll(this.hooksAfter);
        }

        public String toString() {
            return "{name=" + name + (callback != null ? ",callbacked," : ",") + "hooksBefore=" + hooksBefore.toString() + ",hooksAfter=" + hooksAfter.toString() + "}";
        }
    }

    private List<MethodHandle> orderTable = new ArrayList<>();

    public OrderedHookchain(MethodType type) {
        super(type);
    }

    @Override
    public HookData createEmptyHook(String name) {
        return new HookData(name, null);
    }

    /**
     * Performs necessary updates to the state prior to chain execution.
     * <p>
     * This should be called by call.
     */
    private synchronized void fullClean() {
        Set<HookData> remaining = new HashSet<>();
        Set<HookData> toRemoveHook = new HashSet<>();
        Set<String> toAddName = new HashSet<>();
        Set<String> satisfied = new HashSet<>();

        // Clear orderTable
        orderTable.clear();

        // Fill "remaining" set
        remaining.addAll(handleMap.values());

        // Add hooks in waves
        while (!remaining.isEmpty()) {
            // Clear "to add" lists
            toAddName.clear();
            toRemoveHook.clear();

            // Add all necessary hooks
            for (HookData hook : remaining) {
                if (hook.areDependenciesSatisfied(satisfied)) {
                    toAddName.add(hook.name);
                    toRemoveHook.add(hook);
                    if (hook.callback != null) {
                        orderTable.add(hook.callback);
                    }
                }
            }

            // Ensure we have something to add
            if (toAddName.isEmpty()) {
                // TODO: diagnostics
                throw new RuntimeException("cyclic dependency in hookchain");
            }

            // Add/remove things
            satisfied.addAll(toAddName);
            remaining.removeAll(toRemoveHook);
        }

        // Mark clean
        this.dirty = false;
    }

    @Override
    public synchronized void add(String name, MethodHandle callback) {
        if (getHook(name) != null && getHook(name).callback != null) {
            throw new RuntimeException("duplicate hook: " + name);
        }

        this.dirty = true;
        HookData hookData = getOrCreateHook(name);
        hookData.callback = callback;
    }

    @Override
    public synchronized void addConstraint(String nameBefore, String nameAfter) {
        HookData hookBefore = getOrCreateHook(nameBefore);
        HookData hookAfter = getOrCreateHook(nameAfter);

        hookBefore.addHookAfter(nameAfter);
        hookAfter.addHookBefore(nameBefore);
        this.dirty = true;
    }

    /**
     * This will reconstruct the order table if any hooks or hook constraints have changed.
     */
    @Override
    public synchronized void call(Object... args) {
        // orderTable must not be dirty when we actually do this
        if (this.dirty) {
            this.fullClean();
        }

        // Make extra sure
        assert (!this.dirty);

        // Call our callbacks
        for (MethodHandle cb : this.orderTable) {
            assert (cb != null);
            try {
                cb.invokeWithArguments(args);
            } catch (Throwable t) {
                throw new RuntimeException("hookchain calling error", t);
            }
        }
    }
}
