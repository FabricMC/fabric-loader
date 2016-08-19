package net.fabricmc.base.util.hookchain;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tree-based hookchain implementation. Probably slower than the ordered one.
 *
 * @author greaser, asie
 */
public class TreeHookchain<T> implements IFlexibleHookchain<T> {
    private class HookData<T> {
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

        private void call(T arg) {
            if (callback != null) {
                try {
                    callback.invoke(arg);
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

        public boolean canRun() {
            return hasRun || handlesRunSet.containsAll(this.hooksAfter);
        }

        public String toString() {
            return "{name=" + name + (callback != null ? ",callbacked," : ",") + "hooksBefore=" + hooksBefore.toString() + ",hooksAfter=" + hooksAfter.toString() + "}";
        }
    }

    private Set<String> handlesRunSet = new HashSet<>();
    private Map<String, HookData<T>> handleMap = new HashMap<>();
    private Multimap<String, HookData<T>> handlesByDeps = HashMultimap.create();
    private boolean dirty = true;

    /**
     * Performs necessary updates to the state prior to chain execution.
     * <p>
     * This should be called by callChain.
     */
    private synchronized void fullClean() {
        handlesByDeps.clear();

        for (HookData<T> hook : handleMap.values()) {
            if (hook.hooksAfter.isEmpty()) {
                handlesByDeps.put("", hook);
            } else {
                for (String s : hook.hooksAfter) {
                    handlesByDeps.put(s, hook);
                }
            }
        }

        // Check for cyclic dependencies
        for (String hookName1 : handlesByDeps.keySet()) {
            HookData hook1 = handleMap.get(hookName1);
            for (HookData hook2 : handlesByDeps.get(hookName1)) {
                if (handlesByDeps.get(hook2.name).contains(hook1)) {
                    throw new RuntimeException("cyclic dependency in hookchain");
                }
            }
        }

        // Mark clean
        this.dirty = false;
    }

    /**
     * Gets a HookData for a hook, creating it if necessary.
     *
     * @param name Name of hook to get
     * @returns Expected HookData object
     */
    private synchronized HookData<T> getOrCreateHook(String name) {
        if (!handleMap.containsKey(name)) {
            this.dirty = true;
            HookData<T> ret = new HookData<T>(name, null);
            handleMap.put(name, ret);
            return ret;
        } else {
            return handleMap.get(name);
        }
    }

    private synchronized HookData<T> getHook(String name) {
        return handleMap.get(name);
    }

    @Override
    public synchronized void addHook(String name) {
        addHook(name, null);
    }
    
    @Override
    public synchronized void addHook(String name, @Nullable MethodHandle callback) {
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
        HookData<T> hookBefore = getOrCreateHook(nameBefore);
        HookData<T> hookAfter = getOrCreateHook(nameAfter);

        hookBefore.addHookAfter(nameAfter);
        hookAfter.addHookBefore(nameBefore);
        this.dirty = true;
    }

    @Override
    public synchronized void callChain(T arg) {
        callChain("", arg);
    }

    public synchronized void callChain(String key, T arg) {
        if (this.dirty) {
            this.fullClean();
        }

        assert (!this.dirty);

        if (key.length() > 0) {
            HookData<T> hook = getHook(key);
            if (hook == null) {
                return;
            }
            hook.call(arg);
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
                        data.call(arg);
                        hooksCalled.add(data);
                        hookDepsNew.add(data.name);
                    }
                }
            }

            hooksCalledIter = hookDepsNew.size();
        } while (hooksCalledIter > 0);
    }
}
