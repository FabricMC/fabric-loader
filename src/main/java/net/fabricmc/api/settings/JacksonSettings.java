package net.fabricmc.api.settings;

import java.io.InputStream;
import java.io.OutputStream;

public class JacksonSettings extends Settings<Object> {
    @Override
    public void serialise(InputStream stream) {

    }

    @Override
    public void deserialise(OutputStream stream) {

    }

    public JacksonSettings(String name) {
        super(name);
    }

    public JacksonSettings() {
        super();
    }

    @Override
    protected Settings createSub(String name) {
        return new JacksonSettings(name);
    }

    public static void main(String[] args) {
        JacksonSettings settings = new JacksonSettings();

        settings.set("Setting 1", "Hello world!");

        Setting<String> setting = settings
                .builder(String.class)
                .name("Setting 1")
                .defaultValue("Default value")
                .build();

        Setting<String> setting2 = settings
                .builder(String.class)
                .name("Setting 2")
                .defaultValue("Default value")
                .build();

        System.out.println(setting.getValue());
        System.out.println(setting2.getValue());
    }

}
