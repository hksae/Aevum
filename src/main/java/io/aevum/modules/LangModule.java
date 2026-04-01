package io.aevum.modules;

import io.aevum.compiler.Module;
import io.aevum.core.BuiltinFunction;
import java.util.*;

public class LangModule implements Module {

    @Override
    public Map<String, BuiltinFunction> getFunctions() {
        Map<String, BuiltinFunction> map = new HashMap<>();

        map.put("typeOf", args -> {
            Object obj = args.get(0);
            if (obj == null) return "null";
            if (obj instanceof Byte) return "Byte";
            if (obj instanceof Short) return "Short";
            if (obj instanceof Integer) return "Int";
            if (obj instanceof Long) return "Long";
            if (obj instanceof Float) return "Float";
            if (obj instanceof Double) return "Double";
            if (obj instanceof Character) return "Char";
            if (obj instanceof Boolean) return "Bool";
            if (obj instanceof String) return "String";
            if (obj instanceof Object[]) return "Array";
            return obj.getClass().getSimpleName();
        });

        map.put("exit", args -> {
            int code = ((Number) args.get(0)).intValue();
            System.exit(code);
            return null;
        });

        map.put("len", args -> {
            Object obj = args.get(0);
            if (obj == null) return 0;
            if (obj instanceof String) {
                return ((String) obj).length();
            }
            if (obj instanceof Object[]) {
                return ((Object[]) obj).length;
            }
            throw new RuntimeException("len() not supported for " + obj.getClass().getSimpleName());
        });

        map.put("equals", args -> {
            return Objects.equals(args.get(0), args.get(1));
        });

        map.put("hashCode", args -> {
            return Objects.hashCode(args.get(0));
        });

        map.put("identityHashCode", args -> {
            return System.identityHashCode(args.get(0));
        });

        map.put("isSame", args -> {
            return args.get(0) == args.get(1);
        });

        map.put("toString", args -> {
            return String.valueOf(args.get(0));
        });

        map.put("toInt", args -> {
            Object obj = args.get(0);
            if (obj instanceof Number) return ((Number) obj).intValue();
            try {
                return Integer.parseInt(String.valueOf(obj));
            } catch (NumberFormatException e) {
                return null;
            }
        });

        map.put("toLong", args -> {
            Object obj = args.get(0);
            if (obj instanceof Number) return ((Number) obj).longValue();
            try {
                return Long.parseLong(String.valueOf(obj));
            } catch (NumberFormatException e) {
                return null;
            }
        });

        map.put("toDouble", args -> {
            Object obj = args.get(0);
            if (obj instanceof Number) return ((Number) obj).doubleValue();
            try {
                return Double.parseDouble(String.valueOf(obj));
            } catch (NumberFormatException e) {
                return null;
            }
        });

        map.put("toBool", args -> {
            Object obj = args.get(0);
            if (obj instanceof Boolean) return obj;
            if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
            if (obj instanceof String) return Boolean.parseBoolean((String) obj);
            return obj != null;
        });

        map.put("isNull", args -> {
            return args.get(0) == null;
        });

        map.put("isNotNull", args -> {
            return args.get(0) != null;
        });

        map.put("isNumber", args -> {
            return args.get(0) instanceof Number;
        });

        map.put("isString", args -> {
            return args.get(0) instanceof String;
        });

        map.put("isBool", args -> {
            return args.get(0) instanceof Boolean;
        });

        map.put("isArray", args -> {
            return args.get(0) instanceof Object[];
        });


        return map;
    }

    @Override
    public Map<String, Object> getConstants() {
        return Map.of(
                "MAX_INT", Integer.MAX_VALUE,
                "MIN_INT", Integer.MIN_VALUE,
                "MAX_LONG", Long.MAX_VALUE,
                "MIN_LONG", Long.MIN_VALUE,
                "MAX_DOUBLE", Double.MAX_VALUE,
                "MIN_DOUBLE", Double.MIN_VALUE
        );
    }

    private static class LazyValue<T> {
        private volatile T value;
        private final java.util.function.Supplier<T> supplier;

        LazyValue(java.util.function.Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public T get() {
            if (value == null) {
                synchronized (this) {
                    if (value == null) {
                        value = supplier.get();
                    }
                }
            }
            return value;
        }

        @Override
        public String toString() {
            return value == null ? "Lazy(not initialized)" : "Lazy(" + value + ")";
        }
    }
}