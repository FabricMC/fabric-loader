package net.fabricmc.base.util.hookchain;

import java.lang.invoke.MethodHandle;

public interface IHookchain<T> {
    /**
     * Adds/updates a hook with a callback.
     *
     * @param name     Name of the hook to create or update
     * @param callback Callback for the given hook
     */
    void addHook(String name, MethodHandle callback);

    /**
     * Adds a P-comes-before-Q constraint.
     *
     * @param nameBefore Name of the dependee ("P")
     * @param nameAfter  Name of the dependent ("Q")
     */
    void addConstraint(String nameBefore, String nameAfter);

    /**
     * Calls all hooks in this chain.
     *
     * @param arg Data to be fed to all hooks for processing
     */
    void callChain(T arg);
}
