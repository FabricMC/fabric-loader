package net.fabricmc.base;

import net.fabricmc.api.Hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Ordered hookchain implementation.
 *
 * @author greaser
 */
public class OrderedHookchain<T> {
    /**
     * Central data structure for the ordered hookchain.
     */
    private class HookData<T> {
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
            this.hooksBefore.add(oname);
        }

        public void addHookAfter(String oname) {
            assert (oname != null);
            this.hooksAfter.add(oname);
        }

        public boolean areDependenciesSatisfied(Set<String> dependenciesProvided) {
            assert (dependenciesProvided != null);
            return dependenciesProvided.containsAll(this.hooksAfter);
        }
    }

    private Map<String, HookData<T>> handles = new HashMap<>();
    private List<MethodHandle> orderTable = new ArrayList<>();
    private boolean dirty = true;

    /**
     * Performs necessary updates to the state prior to chain execution.
     * <p>
     * This should be called by callChain.
     */
    private synchronized void fullClean() {
        Set<HookData<T>> remaining = new HashSet<>();
        Set<HookData<T>> toRemoveHook = new HashSet<>();
        Set<String> toAddName = new HashSet<>();
        Set<String> satisfied = new HashSet<>();

        // Clear orderTable
        orderTable.clear();

        // Fill "remaining" set
        remaining.addAll(handles.values());

        // Add hooks in waves
        while (!remaining.isEmpty()) {
            // Clear "to add" lists
            toAddName.clear();
            toRemoveHook.clear();

            // Add all necessary hooks
            for (HookData<T> hook : remaining) {
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

    /**
     * Gets a HookData for a hook, creating it if necessary.
     *
     * @param name Name of hook to get
     * @returns Expected HookData object
     */
    private synchronized HookData<T> getOrCreateHook(String name) {
        if (!handles.containsKey(name)) {
            this.dirty = true;
            HookData<T> ret = new HookData<T>(name, null);
            handles.put(name, ret);
            return ret;
        } else {
            return handles.get(name);
        }
    }

    private synchronized HookData<T> getHook(String name) {
        return handles.get(name);
    }

    /**
     * Adds/updates a hook with a callback.
     *
     * @param name     Name of the hook to create or update
     * @param callback Callback for the given hook
     */
    public synchronized void addHook(String name, MethodHandle callback) {
        if (getHook(name) != null && getHook(name).callback != null) {
            throw new RuntimeException("duplicate hook: " + name);
        }

        this.dirty = true;
        HookData<T> hookData = getOrCreateHook(name);
        hookData.callback = callback;
    }

    /**
     * Adds a P-comes-before-Q constraint.
     *
     * @param nameBefore Name of the dependee ("P")
     * @param nameAfter  Name of the dependent ("Q")
     */
    public synchronized void addConstraint(String nameBefore, String nameAfter) {
        HookData<T> hookBefore = getOrCreateHook(nameBefore);
        HookData<T> hookAfter = getOrCreateHook(nameAfter);

        hookBefore.addHookAfter(nameAfter);
        hookAfter.addHookBefore(nameBefore);
        this.dirty = true;
    }

    public synchronized void addHook(Hook hook, MethodHandle callback) {
        addHook(hook.name(), callback);
        for (String s : hook.before()) {
            addConstraint(hook.name(), s);
        }
        for (String s : hook.after()) {
            addConstraint(s, hook.name());
        }
    }

    public synchronized void addAllHooks(Object o) {
        Class c = o.getClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (Method m : c.getMethods()) {
            if (m.isAnnotationPresent(Hook.class)
                    && m.getParameterCount() == 1) {
                try {
                    Hook hook = m.getAnnotation(Hook.class);
                    MethodHandle handle = lookup.unreflect(m).bindTo(o);

                    addHook(hook, handle);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Calls all hooks in this chain.
     * <p>
     * This will reconstruct the order table if any hooks or hook constraints have changed.
     *
     * @param arg Data to be fed to all hooks for processing
     */
    public synchronized void callChain(T arg) {
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
                cb.invoke(arg);
            } catch (Throwable t) {
                throw new RuntimeException("hookchain calling error", t);
            }
        }
    }
}
