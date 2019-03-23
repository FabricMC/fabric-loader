package net.fabricmc.api.settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public abstract class Settings<S> {

    private String name;
    private HashMap<String, Settings> subSettingsHashMap = new HashMap<>();
    private HashMap<String, Setting> settingHashMap = new HashMap<>();
    private HashMap<String, Object> cachedValueMap = new HashMap<>();

    public Settings(String name) {
        this.name = name;
    }

    public Settings() {
        this(null);
    }

    public SettingBuilder<S, Object> builder() {
        return builder(Object.class);
    }

    public <T> SettingBuilder<S, T> builder(Class<T> clazz) {
        return new SettingBuilder<>(this, clazz);
    }

    public Settings sub(String name) {
        if (!subSettingsHashMap.containsKey(name)) {
            subSettingsHashMap.put(name, createSub(name));
        }
        return subSettingsHashMap.get(name);
    }

    public void set(String name, Object value) {
        if (hasSetting(name)) {
            if (attemptSet(name, value)) return;
        }
        cachedValueMap.put(name, value);
    }

    public boolean hasSetting(String name) {
        return settingHashMap.containsKey(name);
    }

    public Setting getSetting(String name) {
        return settingHashMap.get(name);
    }

    <T> void registerAndRecover(Setting<T> setting) {
        String name = setting.getName();
        settingHashMap.put(name, setting);
        if (cachedValueMap.containsKey(name)) {
            attemptSet(name, cachedValueMap.get(name));
        }
    }

    private boolean attemptSet(String name, Object value) {
        if (!getSetting(name).getType().isAssignableFrom(value.getClass())) return false;
        getSetting(name).setValue(value);
        return true;
    }

    protected abstract Settings<S> createSub(String name);

    public abstract void serialise(InputStream stream);
    public abstract void deserialise(OutputStream stream);

    public String getName() {
        return name;
    }

}
