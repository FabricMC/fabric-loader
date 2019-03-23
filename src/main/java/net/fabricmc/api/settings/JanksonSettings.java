package net.fabricmc.api.settings;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.impl.SyntaxError;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class JanksonSettings extends Settings<JsonElement> {

	public JanksonSettings(String name) {
		super(name);
	}

	public JanksonSettings() {
		super();
	}

	@Override
	public void deserialise(InputStream stream, boolean compressed) throws IOException {
		Jankson jankson = Jankson.builder().build();
		try {
			JsonElement element = jankson.load(stream);
			this.deserialise(element);
		} catch (SyntaxError syntaxError) {
			syntaxError.printStackTrace();
		}
	}

	private void deserialise(JsonElement element) {
		if (!(element instanceof JsonObject)) {
			throw new IllegalStateException("Root of configuration must be a jankson object");
		}

		JsonObject object = (JsonObject) element;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String key = entry.getKey();
			JsonElement child = entry.getValue();

			if (child instanceof JsonObject) {
				((JanksonSettings) sub(key)).deserialise(child);
			} else {
				set(key, child);
			}
		}
	}

	@Override
	public void serialise(OutputStream stream, boolean compress) throws IOException {
		JsonObject object = serialise();
		stream.write(object.toJson(!compress, !compress).getBytes());
	}

	private JsonObject serialise() {
		JsonObject object = new JsonObject();
		getCachedValueMap().forEach((s, value) -> object.put(s, ((JsonElement) value)));
		getSettingHashMap().forEach((s, setting) -> {
			object.put(s, (JsonElement) setting.getConverter().serialise(setting.getValue()));
			if (setting.hasComment())
				object.setComment(s, setting.getComment());
		});
		getSubSettingsHashMap().forEach((s, settings) -> object.put(s, ((JanksonSettings) settings).serialise()));
		return object;
	}

	private JsonElement serialiseSingle(Object value) {
		// TODO: Custom serialisers and more complex structures like identifiers
		return new JsonPrimitive(value);
	}

	@Override
	public <T> Converter<JsonElement, T> provideConverter(Class<T> type) {
		return new Converter<JsonElement, T>() {
			@Override
			public T deserialise(JsonElement data) {
				return (T) ((JsonPrimitive) data).getValue(); // this couldn't be more unsafe
			}

			@Override
			public JsonElement serialise(T object) {
				return new JsonPrimitive(object);
			}
		};
	}

	@Override
	protected Settings createSub(String name) {
		return new JanksonSettings(name);
	}

}
