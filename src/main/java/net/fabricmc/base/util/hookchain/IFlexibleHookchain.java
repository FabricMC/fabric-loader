package net.fabricmc.base.util.hookchain;

public interface IFlexibleHookchain extends IHookchain {
    /**
     * Adds/updates a hook without a callback. It is expected that you mark
     * such hooks as executed via call(name, arg).
     *
     * @param name     Name of the hook to create or update
     */
    void add(String name);

    /**
     * Calls a specific hook in the chain and all hooks with satisfied
     * dependencies which depend on it in a recursive manner.
     *
     * @param name Name of the hook to call.
     * @param args  Data to be fed to all hooks for processing
     */
    void call(String name, Object... args);
}
