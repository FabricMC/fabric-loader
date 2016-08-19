package net.fabricmc.base.util.hookchain;

import net.fabricmc.api.Hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public final class HookchainUtils {
    private HookchainUtils() {

    }

    public static void addHook(IHookchain chain, Hook hook, MethodHandle callback) {
        synchronized (chain) {
            chain.add(hook.name(), callback);
            for (String s : hook.before()) {
                chain.addConstraint(s, hook.name());
            }
            for (String s : hook.after()) {
                chain.addConstraint(hook.name(), s);
            }
        }
    }

    public static void addAnnotatedHooks(IHookchain chain, Object o) {
        Class c = o.getClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (Method m : c.getMethods()) {
            if (m.isAnnotationPresent(Hook.class)) {
                try {
                    MethodHandle handle = lookup.unreflect(m).bindTo(o);
                    if (handle.type().equals(chain.getMethodType())) {
                        Hook hook = m.getAnnotation(Hook.class);
                        addHook(chain, hook, handle);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
