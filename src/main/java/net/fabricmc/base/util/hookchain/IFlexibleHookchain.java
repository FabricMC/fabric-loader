package net.fabricmc.base.util.hookchain;

import java.lang.invoke.MethodHandle;

public interface IFlexibleHookchain<T> extends IHookchain<T> {
    /**
     * Adds/updates a hook without a callback. It is expected that you mark
     * such hooks as executed via callChain(name, arg).
     *
     * @param name     Name of the hook to create or update
     * @param callback Callback for the given hook
     */
    void addHook(String name, MethodHandle callback);

    /**
     * Calls a specific hook in the chain and all hooks with satisfied
     * dependencies which depend on it in a recursive manner.
     *
     * @param name Name of the hook to call.
     * @param arg  Data to be fed to all hooks for processing
     */
    void callChain(String name, T arg);
}
