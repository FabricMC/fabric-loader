package net.fabricmc.base.util.mixin;

import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class MixinMappingProviderTiny extends MappingProvider {
    private final String from, to;

    private static class SimpleClassMapper extends Remapper {
        final Map<String, String> classMap;

        public SimpleClassMapper(Map<String, String> map) {
            this.classMap = map;
        }

        @Override
        public String map(String typeName) {
            return classMap.getOrDefault(typeName, typeName);
        }
    }

    public MixinMappingProviderTiny(Messager messager, Filer filer, String from, String to) {
        super(messager, filer);
        this.from = from;
        this.to = to;
    }

    private static final String[] removeFirst(String[] src, int count) {
        if (count >= src.length) {
            return new String[0];
        } else {
            String[] out = new String[src.length - count];
            System.arraycopy(src, count, out, 0, out.length);
            return out;
        }
    }

    // TODO: Unify with tiny-remapper

    @Override
    public void read(File input) throws IOException {
        BufferedReader reader = Files.newBufferedReader(input.toPath());
        String[] header = reader.readLine().split("\t");
        if (header.length <= 1
                || !header[0].equals("v1")) {
            throw new IOException("Invalid mapping version!");
        }

        List<String> headerList = Arrays.asList(removeFirst(header, 1));
        int fromIndex = headerList.indexOf(from);
        int toIndex = headerList.indexOf(to);

        if (fromIndex < 0) throw new IOException("Could not find mapping '" + from + "'!");
        if (toIndex < 0) throw new IOException("Could not find mapping '" + to + "'!");

        Map<String, String> obfFrom = new HashMap<>();
        Map<String, String> obfTo = new HashMap<>();
        List<String[]> linesStageTwo = new ArrayList<>();

        String line;
        while ((line = reader.readLine()) != null) {
            String[] splitLine = line.split("\t");
            if (splitLine.length >= 2) {
                if ("CLASS".equals(splitLine[0])) {
                    classMap.put(splitLine[1 + fromIndex], splitLine[1 + toIndex]);
                    obfFrom.put(splitLine[1], splitLine[1 + fromIndex]);
                    obfTo.put(splitLine[1], splitLine[1 + toIndex]);
                } else {
                    linesStageTwo.add(splitLine);
                }
            }
        }

        SimpleClassMapper descObfFrom = new SimpleClassMapper(obfFrom);

        for (String[] splitLine : linesStageTwo) {
            if ("FIELD".equals(splitLine[0])) {
                String ownerObf = obfFrom.get(splitLine[1]);
                String ownerDeobf = obfTo.get(splitLine[1]);
                String descObf = splitLine[2];
                String descDeobf = descObfFrom.mapDesc(splitLine[2]);
                fieldMap.put(
                        new MappingField(ownerObf, splitLine[3 + fromIndex], descObf),
                        new MappingField(ownerDeobf, splitLine[3 + toIndex], descDeobf)
                );
            } else if ("METHOD".equals(splitLine[0])) {
                String ownerObf = obfFrom.get(splitLine[1]);
                String ownerDeobf = obfTo.get(splitLine[1]);
                String descObf = splitLine[2];
                String descDeobf = descObfFrom.mapMethodDesc(splitLine[2]);
                methodMap.put(
                        new MappingMethod(ownerObf, splitLine[3 + fromIndex], descObf),
                        new MappingMethod(ownerDeobf, splitLine[3 + toIndex], descDeobf)
                );
            }
        }
    }
}
