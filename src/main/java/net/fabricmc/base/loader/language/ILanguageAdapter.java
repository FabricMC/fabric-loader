package net.fabricmc.base.loader.language;

public interface ILanguageAdapter {

    Object createModInstance(Class<?> modClass);

    void callInitializationMethod(Object mod);

}
