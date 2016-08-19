package net.fabricmc.base.util.hookchain;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;

/**
 * Tree-based hookchain implementation. Probably slower than the ordered one.
 *
 * @author greaser, asie
 */
public class TreeHookchain extends Hookchain<TreeHookchain.HookData> implements IFlexibleHookchain {
    protected class HookData {
        public final String name;
        public MethodHandle callback;
        private boolean hasRun = false;
        private final Set<String> hooksBefore = new HashSet<String>();
        private final Set<String> hooksAfter = new HashSet<String>();

        public HookData(String name, MethodHandle callback) {
            assert (name != null);
            assert (callback != null);

            this.name = name;
            this.callback = callback;
        }

        private void call(Object... args) {
            if (callback != null) {
                try {
                    callback.invokeWithArguments(args);
                } catch (Throwable t) {
                    throw new RuntimeException("hookchain calling error", t);
                }
            }
            handlesRunSet.add(name);
            hasRun = true;
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

        public boolean canRun() {
            return hasRun || handlesRunSet.containsAll(this.hooksAfter);
        }

        public String toString() {
            return "{name=" + name + (callback != null ? ",callbacked," : ",") + "hooksBefore=" + hooksBefore.toString() + ",hooksAfter=" + hooksAfter.toString() + "}";
        }
    }

    private Set<String> handlesRunSet = new HashSet<>();
    private Multimap<String, HookData> handlesByDeps = HashMultimap.create();

    public TreeHookchain(MethodType type) {
        super(type);
    }

    @Override
    public HookData createEmptyHook(String name) {
        return new HookData(name, null);
    }

    // TODO: Verify (honestly, I just copied the OrderedHookchain algorithm)
    private synchronized boolean hasCyclicDependencies() {
        Set<HookData> remaining = new HashSet<>();
        Set<HookData> toRemoveHook = new HashSet<>();
        Set<String> toAddName = new HashSet<>();
        Set<String> satisfied = new HashSet<>();

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
                }
            }

            // Ensure we have something to add
            if (toAddName.isEmpty()) {
                return true;
            }

            // Add/remove things
            satisfied.addAll(toAddName);
            remaining.removeAll(toRemoveHook);
        }

        return false;
    }

    /**
     * Performs necessary updates to the state prior to chain execution.
     * <p>
     * This should be called by call.
     */
    private synchronized void fullClean() {
        handlesByDeps.clear();

        for (HookData hook : handleMap.values()) {
            if (hook.hooksAfter.isEmpty()) {
                handlesByDeps.put("", hook);
            } else {
                for (String s : hook.hooksAfter) {
                    handlesByDeps.put(s, hook);
                }
            }
        }

        if (hasCyclicDependencies()) {
            throw new RuntimeException("cyclic dependency in hookchain");
        }

        // Mark clean
        this.dirty = false;
    }

    @Override
    public synchronized void add(String name) {
        add(name, null);
    }

    @Override
    public synchronized void add(String name, @Nullable MethodHandle callback) {
        HookData hookData = getHook(name);

        if (hookData != null && hookData.callback != null && !hookData.callback.equals(callback)) {
            throw new RuntimeException("duplicate hook: " + name);
        }

        this.dirty = true;
        if (hookData == null) {
            hookData = getOrCreateHook(name);
        }
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

    @Override
    public synchronized void call(Object... args) {
        call("", args);
    }

    @Override
    public synchronized void call(String key, Object... args) {
        if (this.dirty) {
            this.fullClean();
        }

        assert (!this.dirty);

        if (key.length() > 0) {
            HookData hook = getHook(key);
            if (hook == null) {
                return;
            }
            hook.call(args);
        }

        int hooksCalledIter;
        Set<HookData> hooksCalled = new HashSet<>();
        Set<String> hookDeps = new HashSet<>();
        Set<String> hookDepsNew = new HashSet<>();
        hookDepsNew.add(key);

        do {
            // clear
            hookDeps.clear();

            // swap sets
            Set<String> swap = hookDeps;
            hookDeps = hookDepsNew;
            hookDepsNew = swap;

            for (String k : hookDeps) {
                for (HookData data : handlesByDeps.get(k)) {
                    if (data.callback != null && !hooksCalled.contains(data) && data.canRun()) {
                        data.call(args);
                        hooksCalled.add(data);
                        hookDepsNew.add(data.name);
                    }
                }
            }

            hooksCalledIter = hookDepsNew.size();
        } while (hooksCalledIter > 0);
    }
}
