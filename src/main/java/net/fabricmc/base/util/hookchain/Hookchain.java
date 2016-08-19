package net.fabricmc.base.util.hookchain;

import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

public abstract class Hookchain<T> implements IHookchain {
    protected boolean dirty = true;
    protected Map<String, T> handleMap = new HashMap<>();
    private final MethodType methodType;

    public Hookchain(MethodType type) {
        this.methodType = type;
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    public abstract T createEmptyHook(String name);

    /**
     * Gets a HookData for a hook, creating it if necessary.
     *
     * @param name Name of hook to get
     * @returns Expected HookData object
     */
    protected synchronized T getOrCreateHook(String name) {
        if (!handleMap.containsKey(name)) {
            this.dirty = true;
            T ret = createEmptyHook(name);
            handleMap.put(name, ret);
            return ret;
        } else {
            return handleMap.get(name);
        }
    }

    protected synchronized T getHook(String name) {
        return handleMap.get(name);
    }
}
