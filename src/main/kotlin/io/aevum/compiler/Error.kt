package io.aevum.compiler

data class Position(
    val line: Int,
    val column: Int,
    val lineContent: String = ""
) {
    override fun toString(): String = "$line:$column"
}

sealed class CompileError(
    val position: Position,
    override val message: String
) : RuntimeException() {

    fun format(): String = buildString {
        appendLine()
        append("${getErrorType()}: ${message}")
        appendLine("\n    at ${position.line}:${position.column}")

        if (position.lineContent.isNotEmpty()) {
            appendLine("    ${position.lineContent}")
            append("    ")
            repeat(position.column - 1) { append(" ") }
            append("^")
            appendLine()
        }
    }

    protected abstract fun getErrorType(): String
}


class LexerError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Lexer error"
}


class ParserError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Syntax error"
}


class CompilerError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Compiler error"
}

class TypeError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Type error"
}

class VariableError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Variable error"
}

class ReferenceError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Reference error"
}

class FunctionError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Function error"
}

class VisibilityError(position: Position, message: String) : CompileError(position, message) {
    override fun getErrorType(): String = "Visibility error"
}


class VMRuntimeError(val bytecodePosition: Int, message: String) :
    RuntimeException("Runtime error at bytecode $bytecodePosition: $message")