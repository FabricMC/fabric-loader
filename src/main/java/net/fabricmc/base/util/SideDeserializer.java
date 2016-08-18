package net.fabricmc.base.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.fabricmc.api.Side;

import java.lang.reflect.Type;

public class SideDeserializer implements JsonDeserializer<Side> {

    @Override
    public Side deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Side.valueOf(element.getAsString().toUpperCase());
    }

}
