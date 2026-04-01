package io.aevum.compiler

import io.aevum.compiler.TokenType.*

class Lexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1
    private var startColumn = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            startColumn = column
            scanToken()
        }
        tokens.add(Token(EOF, "", null, line, column, getLineContent(line)))
        return tokens
    }

    private fun getLineContent(lineNum: Int): String {
        val lines = source.split("\n")
        return if (lineNum - 1 in lines.indices) lines[lineNum - 1] else ""
    }

    private fun error(message: String): Nothing {
        throw LexerError(
            Position(line, column, getLineContent(line)),
            message
        )
    }

    private fun consume(expected: Char, message: String) {
        if (isAtEnd() || peek() != expected) {
            error(message)
        }
        advance()
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '[' -> addToken(LBRACKET)
            ']' -> addToken(RBRACKET)
            ',' -> addToken(COMMA)
            '+' -> addToken(PLUS)
            '-' -> addToken(MINUS)
            '*' -> addToken(STAR)
            ':' -> addToken(COLON)
            '?' -> addToken(NULLABLE)
            ';' -> addToken(SEMICOLON)
            '{' -> addToken(LBRACE)
            '}' -> addToken(RBRACE)
            '%' -> addToken(MOD)
            '.' -> {
                if (match('.')) {
                    addToken(DOT_DOT)
                } else {
                    addToken(DOT)
                }
            }
            '=' -> {
                if (match('=')) addToken(EQUALS_EQUALS)
                else addToken(EQUALS)
            }
            '!' -> {
                if (match('=')) addToken(BANG_EQUALS)
                else addToken(BANG)
            }
            '>' -> {
                if (match('=')) addToken(GREATER_EQUALS)
                else addToken(GREATER)
            }
            '<' -> {
                if (match('=')) addToken(LESS_EQUALS)
                else addToken(LESS)
            }
            '&' -> {
                if (match('&')) addToken(AND)
                else error("Expected '&&'")
            }
            '|' -> {
                if (match('|')) addToken(OR)
                else error("Expected '||'")
            }
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else {
                    addToken(SLASH)
                }
            }
            '"', '\'' -> string(c)
            ' ', '\r', '\t' -> {}
            '\n' -> {
                line++
                column = 1
            }
            else -> {
                if (c.isDigit()) {
                    number()
                } else if (isAlpha(c)) {
                    identifier()
                } else {
                    error("Unexpected character '${c}'")
                }
            }
        }
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        val type = when (text) {
            "var" -> VAR
            "val" -> VAL
            "if" -> IF
            "else" -> ELSE
            "while" -> WHILE
            "true" -> TRUE
            "false" -> FALSE
            "null" -> NULL
            "fun" -> FUN
            "return" -> RETURN
            "class" -> CLASS
            "extends" -> EXTENDS
            "implements" -> IMPLEMENTS
            "interface" -> INTERFACE
            "abstract" -> ABSTRACT
            "override" -> OVERRIDE
            "super" -> SUPER
            "this" -> THIS
            "new" -> NEW
            "constructor" -> CONSTRUCTOR
            "public" -> PUBLIC
            "private" -> PRIVATE
            "protected" -> PROTECTED
            "import" -> IMPORT
            "as" -> AS
            "for" -> FOR
            "in" -> IN
            "until" -> UNTIL
            "step" -> STEP
            "downTo" -> DOWNTO
            "break" -> BREAK
            "continue" -> CONTINUE
            "pass" -> PASS
            "shadow" -> SHADOW
            else -> ID
        }
        addToken(type)
    }

    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun number() {
        var isFloat = false

        while (peek().isDigit()) advance()

        if (peek() == '.') {

            if (peekNext().isDigit()) {
                isFloat = true
                advance()
                while (peek().isDigit()) advance()
            }

        }

        val value = source.substring(start, current)
        val remaining = if (current < source.length) source.substring(current) else ""

        val suffix = when {
            remaining.startsWith("UL", true) -> {
                current += 2
                "ULong"
            }
            remaining.startsWith("UB", true) -> {
                current += 2
                "UByte"
            }
            remaining.startsWith("US", true) -> {
                current += 2
                "UShort"
            }
            remaining.startsWith("U", true) -> {
                current += 1
                "UInt"
            }
            remaining.startsWith("L", true) -> {
                current += 1
                "Long"
            }
            remaining.startsWith("B", true) -> {
                current += 1
                "Byte"
            }
            remaining.startsWith("S", true) -> {
                current += 1
                "Short"
            }
            remaining.startsWith("F", true) -> {
                current += 1
                "Float"
            }
            else -> if (isFloat) "Double" else "Int"
        }

        val cleanValue = value

        val literal = when (suffix) {
            "Byte" -> cleanValue.toByte()
            "Short" -> cleanValue.toShort()
            "Long" -> cleanValue.toLong()
            "ULong" -> ULong(java.math.BigInteger(cleanValue))
            "UByte" -> {
                val intValue = cleanValue.toInt()
                if (intValue < 0 || intValue > 0xFF) {
                    error("UByte value out of range: $cleanValue")
                }
                UByte(intValue.toByte())
            }
            "UShort" -> {
                val intValue = cleanValue.toInt()
                if (intValue < 0 || intValue > 0xFFFF) {
                    error("UShort value out of range: $cleanValue")
                }
                UShort(intValue.toShort())
            }
            "UInt" -> {
                val longValue = cleanValue.toLong()
                if (longValue < 0 || longValue > 0xFFFFFFFFL) {
                    error("UInt value out of range: $cleanValue")
                }
                UInt(longValue.toInt())
            }
            "Float" -> cleanValue.toFloat()
            "Double" -> value.toDouble()
            else -> value.toInt()
        }

        addToken(NUMBER, literal)
    }

    private fun string(quote: Char) {
        val sb = StringBuilder()

        while (peek() != quote && !isAtEnd()) {
            if (peek() == '\n') error("Unclosed string")

            if (peek() == '\\') {
                advance()
                val escaped = when (peek()) {
                    'n' -> '\n'
                    't' -> '\t'
                    '\\' -> '\\'
                    '\'' -> '\''
                    '"' -> '"'
                    else -> error("Unknown escape sequence: \\${peek()}")
                }
                sb.append(escaped)
                advance()
            } else {
                sb.append(peek())
                advance()
            }
        }

        if (isAtEnd()) error("Unclosed string")
        advance()

        val value = sb.toString()
        addToken(STRING, value)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        column++
        return true
    }

    private fun advance(): Char {
        val c = source[current]
        current++
        column++
        return c
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun isAtEnd(): Boolean = current >= source.length
    private fun isAlpha(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun isAlphaNumeric(c: Char) = isAlpha(c) || c.isDigit()

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(
            Token(
                type = type,
                lexeme = text,
                literal = literal,
                line = line,
                column = startColumn,
                lineContent = getLineContent(line)
            )
        )
    }
}