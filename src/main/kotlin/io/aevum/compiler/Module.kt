package io.aevum.compiler

import io.aevum.core.BuiltinFunction

interface Module {
    val functions: Map<String, BuiltinFunction>
    val constants: Map<String, Any?>
}