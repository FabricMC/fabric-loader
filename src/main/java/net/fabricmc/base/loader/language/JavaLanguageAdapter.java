package net.fabricmc.base.loader.language;

import net.fabricmc.base.loader.Init;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class JavaLanguageAdapter implements ILanguageAdapter {

    @Override
    public Object createModInstance(Class<?> modClass) {
        try {
            Constructor<?> constructor = modClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void callInitializationMethod(Object mod) {
        for (Method m : mod.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(Init.class) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    m.invoke(mod);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
