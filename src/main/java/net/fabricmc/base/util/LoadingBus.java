package net.fabricmc.base.util;

import net.fabricmc.base.util.hookchain.HookchainUtils;
import net.fabricmc.base.util.hookchain.IFlexibleHookchain;
import net.fabricmc.base.util.hookchain.TreeHookchain;

import java.lang.invoke.MethodType;

/**
 * Created by asie on 8/19/16.
 */
public class LoadingBus {
    private final IFlexibleHookchain hookchain;

    public LoadingBus() {
        hookchain = new TreeHookchain(MethodType.methodType(void.class));
    }

    public void register(Object o) {
        HookchainUtils.addAnnotatedHooks(hookchain, o);
    }

    public void registerHookName(String mark) {
        hookchain.add(mark);
    }

    public void start() {
        hookchain.call();
    }

    public void call(String mark) {
        hookchain.call(mark);
    }
}
