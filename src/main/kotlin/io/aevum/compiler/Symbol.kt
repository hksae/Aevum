package io.aevum.compiler

data class Symbol(
    val name: String,
    val type: Type,
    val index: Int,
    val isMutable: Boolean
)