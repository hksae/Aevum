package io.aevum.modules;

import io.aevum.compiler.Module;
import io.aevum.core.BuiltinFunction;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class IoModule implements Module {

    @Override
    public Map<String, BuiltinFunction> getFunctions() {
        Map<String, BuiltinFunction> map = new HashMap<>();
        map.put("println", args -> {
            if (args.isEmpty()) {
                System.out.println();
                return null;
            }
            System.out.println(args.get(0));
            return null;
        });

        map.put("print", args -> {
            if (args.isEmpty()) {
                System.out.print("");
                return null;
            }
            System.out.print(args.get(0));
            return null;
        });

        map.put("printErr", args -> {
            System.err.print(args.get(0));
            return null;
        });

        map.put("printlnErr", args -> {
            System.err.println(args.get(0));
            return null;
        });
        map.put("readln", args -> {
            return new Scanner(System.in).nextLine();
        });

        map.put("readInt", args -> {
            return new Scanner(System.in).nextInt();
        });

        map.put("readDouble", args -> {
            return new Scanner(System.in).nextDouble();
        });
        return map;
    }

    @Override
    public Map<String, Object> getConstants() {
        return Map.of(
                "LINE_SEPARATOR", System.lineSeparator(),
                "FILE_SEPARATOR", File.separator
        );
    }
}