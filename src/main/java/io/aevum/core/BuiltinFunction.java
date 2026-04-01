package io.aevum.core;

import java.util.List;

public interface BuiltinFunction {
    Object call(List<Object> args);
}