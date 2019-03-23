package net.fabricmc.api.settings;

import java.io.InputStream;
import java.io.OutputStream;

public class JacksonSettings extends Settings<Object> {

	public JacksonSettings(String name) {
		super(name);
	}

	public JacksonSettings() {
		super();
	}

    @Override
    public void serialise(InputStream stream) {

    }

    @Override
    public void deserialise(OutputStream stream) {

	}

    @Override
    protected Settings createSub(String name) {
        return new JacksonSettings(name);
    }

}
