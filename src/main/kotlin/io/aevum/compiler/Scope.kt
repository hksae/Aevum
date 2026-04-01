package io.aevum.compiler

class Scope(val parent: Scope? = null) {
    private val symbols = mutableMapOf<String, Symbol>()
    private val fields = mutableMapOf<String, FieldInfo>()

    fun define(name: String, type: Type, isMutable: Boolean, index: Int): Symbol {
        val symbol = Symbol(name, type, index, isMutable)
        symbols[name] = symbol
        return symbol
    }

    fun defineField(name: String, type: Type, isMutable: Boolean) {
        fields[name] = FieldInfo(name, type, isMutable)
    }

    fun resolve(name: String): Symbol? {
        return symbols[name] ?: parent?.resolve(name)
    }

    fun resolveField(name: String): FieldInfo? {
        return fields[name] ?: parent?.resolveField(name)
    }

    fun resolveLocal(name: String): Symbol? = symbols[name]

    fun allSymbols(): Map<String, Symbol> = symbols
}

data class FieldInfo(
    val name: String,
    val type: Type,
    val isMutable: Boolean
)