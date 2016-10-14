package net.fabricmc.base.util.mixin;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.fabricmc.tinyremapper.TinyUtils;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MixinDevRemapper implements IRemapper {
    private final BiMap<String, String> classMap = HashBiMap.create();
    private final Map<TinyUtils.Mapping, TinyUtils.Mapping> fieldMap = new HashMap<>();
    private final Map<TinyUtils.Mapping, TinyUtils.Mapping> methodMap = new HashMap<>();

    private final SimpleClassMapper classMapper = new SimpleClassMapper(classMap);
    private final SimpleClassMapper classUnmapper = new SimpleClassMapper(classMap.inverse());

    private static class SimpleClassMapper extends Remapper {
        final Map<String, String> classMap;

        public SimpleClassMapper(Map<String, String> map) {
            this.classMap = map;
        }

        public String map(String typeName) {
            return this.classMap.getOrDefault(typeName, typeName);
        }
    }

    public void readMapping(BufferedReader reader, String fromM, String toM) throws IOException {
        TinyUtils.read(reader, fromM, toM, classMap::put, fieldMap::put, methodMap::put);
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        TinyUtils.Mapping mapping = methodMap.get(new TinyUtils.Mapping(owner, name, desc));
        return mapping != null ? mapping.name : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        TinyUtils.Mapping mapping = fieldMap.get(new TinyUtils.Mapping(owner, name, desc));
        return mapping != null ? mapping.name : name;
    }

    @Override
    public String map(String typeName) {
        return classMap.getOrDefault(typeName, typeName);
    }

    @Override
    public String unmap(String typeName) {
        return classMap.inverse().getOrDefault(typeName, typeName);
    }

    @Override
    public String mapDesc(String desc) {
        return classMapper.mapDesc(desc);
    }

    @Override
    public String unmapDesc(String desc) {
        return classUnmapper.mapDesc(desc);
    }
}
