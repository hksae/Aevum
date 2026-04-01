package io.aevum.compiler

import io.aevum.core.BuiltinFunction

class ModuleLoader {

    private val cache = mutableMapOf<String, Module>()
    private val loading = mutableSetOf<String>()

    fun loadModule(name: String, position: Position): Module {
        cache[name]?.let { return it }

        if (name in loading) {
            throw CompilerError(position, "Circular import detected: $name")
        }

        loading.add(name)

        val module = loadJavaModule(name, position)

        loading.remove(name)
        cache[name] = module
        return module
    }

    private fun loadJavaModule(name: String, position: Position): Module {

        val className = when (name) {
            "time" -> "io.aevum.modules.TimeModule"
            else -> "io.aevum.modules.${name.capitalize()}Module"
        }
        return try {
            val clazz = Class.forName(className)
            clazz.getDeclaredConstructor().newInstance() as Module
        } catch (e: ClassNotFoundException) {
            throw CompilerError(position, "Module not found: $name")
        } catch (e: Exception) {
            throw CompilerError(position, "Failed to load module: $name")
        }
    }
}