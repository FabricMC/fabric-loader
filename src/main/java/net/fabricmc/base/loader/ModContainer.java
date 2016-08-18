package net.fabricmc.base.loader;

import net.fabricmc.base.loader.language.ILanguageAdapter;

public class ModContainer {

    private ModInfo info;
    private ILanguageAdapter adapter;
    private Object instance;

    public ModContainer(ModInfo info) {
        this.info = info;
        if (!info.getModClass().isEmpty()) {
            this.adapter = createAdapter();
            this.instance = createInstance();
        }
    }

    public void initialize() {
        adapter.callInitializationMethod(instance);
    }

    public boolean hasModObject() {
        return instance != null;
    }

    public ModInfo getInfo() {
        return info;
    }

    public ILanguageAdapter getAdapter() {
        return adapter;
    }

    public Object getInstance() {
        return instance;
    }

    private ILanguageAdapter createAdapter() {
        try {
            return (ILanguageAdapter)Class.forName(info.getLanguageAdapter()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create language adapter %s for mod %s.%s", info.getLanguageAdapter(), info.getGroup(), info.getId()), e);
        }
    }

    private Object createInstance() {
        try {
            return adapter.createModInstance(Class.forName(info.getModClass()));
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create mod instance for mod %s.%s", info.getGroup(), info.getId()), e);
        }
    }
}
