package io.aevum.compiler

enum class TokenType {
    VAR, VAL,
    ID, NUMBER, STRING,
    TRUE, FALSE, NULL,
    EQUALS, PLUS, MINUS, STAR, SLASH, SEMICOLON,
    LEFT_PAREN, RIGHT_PAREN,
    LBRACKET, RBRACKET, COMMA,
    LBRACE, RBRACE,
    COLON, NULLABLE,
    EQUALS_EQUALS, BANG_EQUALS,
    GREATER, GREATER_EQUALS, LESS, LESS_EQUALS,
    AND, OR, BANG,
    IF, ELSE, WHILE,
    FUN, RETURN,  
    EOF,
    CLASS,      
    EXTENDS,    
    IMPLEMENTS, 
    INTERFACE,  
    ABSTRACT,   
    OVERRIDE,   
    SUPER,      
    THIS,       
    NEW,
    CONSTRUCTOR,
    PUBLIC,     
    PRIVATE,    
    PROTECTED,
    DOT,
    IMPORT, AS,
    FOR, IN,
    DOT_DOT,    
    UNTIL,      
    STEP,       
    DOWNTO,
    MOD,
    BREAK,      
    CONTINUE,
    PASS,
    SHADOW
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int = 0,
    val column: Int = 0,
    val lineContent: String = ""
) {
    fun position(): Position = Position(line, column, lineContent)
}