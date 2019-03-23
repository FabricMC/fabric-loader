package net.fabricmc.api.settings;

import java.io.InputStream;
import java.io.OutputStream;

public class JanksonSettings extends Settings<Object> {

	public JanksonSettings(String name) {
		super(name);
	}

	public JanksonSettings() {
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
        return new JanksonSettings(name);
    }

}
