package net.fabricmc.base.util;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class VersionDeserializer implements JsonDeserializer<Version> {

    @Override
    public Version deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Version.valueOf(element.getAsString());
    }

}
