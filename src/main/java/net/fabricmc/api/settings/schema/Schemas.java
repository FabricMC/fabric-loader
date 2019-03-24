package net.fabricmc.api.settings.schema;

import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import net.fabricmc.api.settings.Setting;
import net.fabricmc.api.settings.Settings;
import net.fabricmc.api.settings.constraint.Constraint;
import net.fabricmc.api.settings.constraint.ValuedConstraint;

import java.util.List;

public class Schemas {

	public static JsonObject createSchema(Settings settings) {
		JsonObject object = new JsonObject();

		settings.getSettingsImmutable().forEach((key, setting) -> object.put((String) key, createSchema((Setting) setting)));
		settings.getSubSettingsImmutable().forEach((key, settingsObject) -> object.put((String) key, createSchema((Settings) settingsObject)));

		return object;
	}

	private static JsonObject createSchema(Setting setting) {
		JsonObject object = new JsonObject();
		object.put("comment", new JsonPrimitive(setting.getComment()));
		object.put("class", new JsonPrimitive(setting.getType().getTypeName()));
		object.put("constraints", createSchema(setting.getConstraintList()));
		return object;
	}

	private static JsonElement createSchema(List<Constraint> constraintList) {
		JsonArray array = new JsonArray();
		for (Constraint constraint : constraintList) {
			JsonObject object = new JsonObject();
			object.put("identifier", new JsonPrimitive(constraint.getType().getIdentifier().toString()));

			if (constraint instanceof ValuedConstraint) {
				object.put("value", new JsonPrimitive(((ValuedConstraint) constraint).getValue()));
			}
			array.add(object);
		}
		return array;
	}

}
