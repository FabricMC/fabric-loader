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

public class JanksonSettings extends Settings<Object> {

	private static final HashMap<Class, Function<Object, Object>> conversions = new HashMap<Class, Function<Object, Object>>() {{
		put(JsonPrimitive.class, element -> {
			JsonPrimitive primitive = (JsonPrimitive) element;
			return primitive.getValue();
		});
	}};

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
				deserialiseSingle(key, child);
			}
		}
	}

	private void deserialiseSingle(String key, JsonElement child) {
		if (conversions.containsKey(child.getClass())) {
			Object value = conversions.get(child.getClass()).apply(child);
			set(key, value);
			return;
		}
		throw new IllegalStateException("Attempted to serialise unsupported node " + child.getClass());
	}

	@Override
	public void serialise(OutputStream stream, boolean compress) throws IOException {
		JsonObject object = serialise();
		stream.write(object.toJson(!compress, !compress).getBytes());
	}

	private JsonObject serialise() {
		JsonObject object = new JsonObject();
		getSettingHashMap().forEach((s, setting) -> {
			object.put(s, serialiseSingle(setting.getValue()));
			object.setComment(s, setting.getComment());
		});
		getCachedValueMap().forEach((s, value) -> object.put(s, serialiseSingle(value)));
		getSubSettingsHashMap().forEach((s, settings) -> object.put(s, ((JanksonSettings) settings).serialise()));
		return object;
	}

	private JsonElement serialiseSingle(Object value) {
		// TODO: Custom serialisers and more complex structures like identifiers
		return new JsonPrimitive(value);
	}

	@Override
	protected Settings createSub(String name) {
		return new JanksonSettings(name);
	}

}
