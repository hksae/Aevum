package io.aevum.compiler

sealed class Expr {
    abstract val type: Type?
    abstract val position: Position

    data class Literal(
        val value: Any?,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Range(
        val start: Expr,
        val end: Expr,
        val isInclusive: Boolean,  
        val step: Expr? = null,     
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Call(
        val name: String,
        val arguments: List<Expr>,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Variable(
        val name: String,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Binary(
        val left: Expr,
        val operator: Token,
        val right: Expr,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Unary(
        val operator: Token,
        val right: Expr,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class ArrayLiteral(
        val elements: List<Expr>,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class ArrayAccess(
        val array: Expr,
        val index: Expr,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class New(
        val className: String,
        val arguments: List<Expr>,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class InRange(
        val value: Expr,
        val range: Range,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class FieldAccess(
        val objectExpr: Expr,
        val fieldName: String,
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class MethodCall(
        val objectExpr: Expr,
        val methodName: String,
        val arguments: List<Expr>,
        override val type: Type? = null,
        override val position: Position

    ) : Expr()

    data class This(
        override val type: Type? = null,
        override val position: Position
    ) : Expr()

    data class Super(
        override val type: Type? = null,
        override val position: Position
    ) : Expr()
}

sealed class Stmt {
    abstract val position: Position

    data class Expression(
        val expr: Expr,
        override val position: Position
    ) : Stmt()

    data class Swap(
        val targets: List<String>,
        val sources: List<Expr>,
        override val position: Position
    ) : Stmt()

    data class Break(
        override val position: Position
    ) : Stmt()

    data class Shadow(
        val variable: String,
        val body: List<Stmt>,
        override val position: Position
    ) : Stmt()

    data class Continue(
        override val position: Position
    ) : Stmt()

    data class Pass(
        override val position: Position
    ) : Stmt()

    data class Import(
        val moduleName: String,
        val alias: String?,
        override val position: Position
    ) : Stmt()

    data class AssignField(
        val target: Expr,   
        val value: Expr,
        override val position: Position
    ) : Stmt()

    data class Call(
        val name: String,
        val arguments: List<Expr>,
        override val position: Position
    ) : Stmt()

    data class ArrayAssignment(
        val target: Expr,
        val value: Expr,
        override val position: Position
    ) : Stmt()

    data class Var(
        val name: String,
        val type: Type,
        val initializer: Expr,
        val isMutable: Boolean,
        override val position: Position
    ) : Stmt()

    data class If(
        val condition: Expr,
        val thenBranch: List<Stmt>,
        val elseBranch: List<Stmt>? = null,
        override val position: Position
    ) : Stmt()

    data class For(
        val variable: String,
        val isMutable: Boolean,
        val iterable: Expr,
        val body: List<Stmt>,
        override val position: Position
    ) : Stmt()

    data class While(
        val condition: Expr,
        val body: List<Stmt>,
        override val position: Position
    ) : Stmt()

    data class Assignment(
        val name: String,
        val value: Expr,
        override val position: Position
    ) : Stmt()

    data class Block(
        val statements: List<Stmt>,
        override val position: Position
    ) : Stmt()

    data class Function(
        val name: String,
        val parameters: List<Parameter>,
        val returnType: Type,
        val body: List<Stmt>,
        override val position: Position
    ) : Stmt()

    data class Return(
        val value: Expr?,
        override val position: Position
    ) : Stmt()


    data class Class(
        val name: String,
        val superClass: String?,
        val interfaces: List<String>,
        val fields: List<Field>,
        val methods: List<Method>,
        override val position: Position
    ) : Stmt()

    data class Interface(
        val name: String,
        val methods: List<Method>,
        override val position: Position
    ) : Stmt()
}

data class Field(
    val name: String,
    val type: Type,
    val isMutable: Boolean,
    val initializer: Expr?,
    val visibility: Visibility,
    val position: Position
)

data class Method(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: List<Stmt>,
    val isOverride: Boolean,
    val isAbstract: Boolean,
    val visibility: Visibility,
    val position: Position
)

data class Parameter(
    val name: String,
    val type: Type,
    val isMutable: Boolean = true
)

enum class Visibility {
    PUBLIC,     
    PRIVATE,    
    PROTECTED   
}