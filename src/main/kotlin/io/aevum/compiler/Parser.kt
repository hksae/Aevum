package io.aevum.compiler

import io.aevum.compiler.TokenType.*

class Parser(private val tokens: List<Token>) {
    private var current = 0


    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            val stmt = declaration()

            if (stmt !is Stmt.Block || stmt.statements.isNotEmpty()) {
                statements.add(stmt)
            }
        }
        return statements
    }

    private fun declaration(): Stmt {

        while (match(SEMICOLON)) { }

        val token = peek()

        if (isAtEnd()) {
            return Stmt.Block(emptyList(), position = Position(0, 0, ""))
        }

        return when {
            match(CLASS) -> classDeclaration()
            match(INTERFACE) -> interfaceDeclaration()
            match(VAR) -> varDeclaration()
            match(VAL) -> valDeclaration()
            match(FUN) -> functionDeclaration()
            match(IMPORT) -> importDeclaration()
            else -> statement()
        }
    }


    private fun varDeclaration(): Stmt {
        val nameToken = consume(ID, "Expected variable name")
        val name = nameToken.lexeme
        val type = if (match(COLON)) {
            parseType()
        } else {
            Type.AnyType
        }
        consume(EQUALS, "Expected '=' after type")
        val initializer = expression()
        consumeOptional(SEMICOLON)
        return Stmt.Var(name, type, initializer, isMutable = true, position = nameToken.position())
    }

    private fun importDeclaration(): Stmt {
        val moduleName = consume(ID, "Expected module name").lexeme
        var alias: String? = null

        if (match(AS)) {
            alias = consume(ID, "Expected alias name").lexeme
        }

        consumeOptional(SEMICOLON)

        return Stmt.Import(moduleName, alias, position = previous().position())
    }

    private fun classDeclaration(): Stmt {
        val classToken = consume(ID, "Expected class name")
        val name = classToken.lexeme

        val constructorParams = if (match(LEFT_PAREN)) {
            val params = parseParameters()
            consume(RIGHT_PAREN, "Expected ')'")
            params
        } else {
            emptyList()
        }

        var superClass: String? = null
        var superClassArgs: List<Expr> = emptyList()

        if (match(EXTENDS)) {
            superClass = consume(ID, "Expected superclass name").lexeme

            if (match(LEFT_PAREN)) {
                superClassArgs = mutableListOf<Expr>()
                if (!check(RIGHT_PAREN)) {
                    do {
                        superClassArgs.add(expression())
                    } while (match(COMMA))
                }
                consume(RIGHT_PAREN, "Expected ')'")
            }
        }

        val interfaces = mutableListOf<String>()
        if (match(IMPLEMENTS)) {
            do {
                interfaces.add(consume(ID, "Expected interface name").lexeme)
            } while (match(COMMA))
        }

        consume(LBRACE, "Expected '{' at start of class body")

        val fields = mutableListOf<Field>()
        val methods = mutableListOf<Method>()
        var constructor: Method? = null

        if (constructorParams.isNotEmpty() || superClass != null) {

            for (param in constructorParams) {
                val field = Field(
                    name = param.name,
                    type = param.type,
                    isMutable = param.isMutable,
                    initializer = null,
                    visibility = Visibility.PUBLIC,
                    position = classToken.position()
                )
                fields.add(field)
            }

            val initStmts = mutableListOf<Stmt>()

            for (param in constructorParams) {
                val thisExpr = Expr.This(position = classToken.position(), type = Type.ClassType(name))
                val fieldAccess = Expr.FieldAccess(
                    objectExpr = thisExpr,
                    fieldName = param.name,
                    position = classToken.position()
                )
                val paramVar = Expr.Variable(param.name, type = param.type, position = classToken.position())
                val assignStmt = Stmt.AssignField(fieldAccess, paramVar, position = classToken.position())
                initStmts.add(assignStmt)
            }

            if (superClass != null) {
                val superExpr = Expr.Super(position = classToken.position())
                val superCall = Expr.MethodCall(
                    objectExpr = superExpr,
                    methodName = "<init>",
                    arguments = superClassArgs,
                    position = classToken.position()
                )

                initStmts.add(0, Stmt.Expression(superCall, position = classToken.position()))
            }

            constructor = Method(
                name = "<init>",
                parameters = constructorParams,
                returnType = Type.UnitType,
                body = initStmts,
                isOverride = false,
                isAbstract = false,
                visibility = Visibility.PUBLIC,
                position = classToken.position()
            )
        }

        while (!check(RBRACE) && !isAtEnd()) {
            when {
                match(PUBLIC, PRIVATE, PROTECTED) -> {
                    val visibility = when (previous().type) {
                        PUBLIC -> Visibility.PUBLIC
                        PRIVATE -> Visibility.PRIVATE
                        PROTECTED -> Visibility.PROTECTED
                        else -> Visibility.PUBLIC
                    }
                    if (match(VAR) || match(VAL)) {
                        val isMutable = previous().type == VAR
                        fields.add(fieldDeclaration(isMutable, visibility))
                    } else {
                        error("Expected var or val after visibility modifier", peek())
                    }
                }
                match(VAR) || match(VAL) -> {
                    val isMutable = previous().type == VAR
                    fields.add(fieldDeclaration(isMutable))
                }
                match(CONSTRUCTOR) -> {
                    constructor = constructorDeclaration()
                }
                else -> {
                    methods.add(methodDeclaration())
                }
            }
        }

        consume(RBRACE, "Expected '}' at end of class body")

        if (constructor != null) {
            methods.add(constructor)
        } else {

            val defaultConstructor = createDefaultConstructor(name, fields, classToken.position())
            methods.add(defaultConstructor)
        }

        return Stmt.Class(name, superClass, interfaces, fields, methods, position = classToken.position())
    }

    private fun parseVisibility(): Visibility {
        return when {
            match(PUBLIC) -> Visibility.PUBLIC
            match(PRIVATE) -> Visibility.PRIVATE
            match(PROTECTED) -> Visibility.PROTECTED
            else -> Visibility.PUBLIC
        }
    }

    private fun createDefaultConstructor(className: String, fields: List<Field>, position: Position): Method {
        val initStmts = mutableListOf<Stmt>()

        for (field in fields) {
            if (field.initializer != null) {

                val thisExpr = Expr.This(position = field.position)
                val fieldAccess = Expr.FieldAccess(
                    objectExpr = thisExpr,
                    fieldName = field.name,
                    position = field.position
                )

                val assignStmt = Stmt.AssignField(
                    target = fieldAccess,
                    value = field.initializer,
                    position = field.position
                )
                initStmts.add(assignStmt)
            }
        }

        return Method(
            name = "<init>",
            parameters = emptyList(),
            returnType = Type.UnitType,
            body = initStmts,
            isOverride = false,
            isAbstract = false,
            position = position,
            visibility = Visibility.PUBLIC
        )
    }

    private fun constructorDeclaration(): Method {
        val constructorToken = previous()
        consume(LEFT_PAREN, "Expected '('")
        val parameters = parseParameters()
        consume(RIGHT_PAREN, "Expected ')'")

        val body = block()

        return Method(
            name = "<init>",
            parameters = parameters,
            returnType = Type.UnitType,
            body = body,
            isOverride = false,
            isAbstract = false,
            position = constructorToken.position(),
            visibility = Visibility.PUBLIC
        )
    }

    private fun fieldDeclaration(isMutable: Boolean, visibility: Visibility = Visibility.PUBLIC): Field {
        val nameToken = consume(ID, "Expected field name")
        val name = nameToken.lexeme
        consume(COLON, "Expected ':' after field name")
        val type = parseType()

        var initializer: Expr? = null
        if (match(EQUALS)) {
            initializer = expression()
        }

        consumeOptional(SEMICOLON)

        return Field(name, type, isMutable, initializer, visibility, position = nameToken.position())
    }

    private fun parseParameters(): List<Parameter> {
        val parameters = mutableListOf<Parameter>()

        if (!check(RIGHT_PAREN)) {
            do {

                var isMutable = true  
                if (match(VAL)) {
                    isMutable = false
                } else if (match(VAR)) {
                    isMutable = true
                }

                val name = consume(ID, "Expected parameter name").lexeme
                consume(COLON, "Expected ':' after parameter name")
                val type = parseType()

                parameters.add(Parameter(name, type, isMutable))
            } while (match(COMMA))
        }

        return parameters
    }

    private fun methodDeclaration(): Method {
        val visibility = parseVisibility()  

        val modifiers = mutableListOf<String>()
        while (match(ABSTRACT, OVERRIDE)) {
            modifiers.add(previous().lexeme)
        }

        if (!match(FUN)) {
            error("Expected 'fun' keyword", peek())
        }

        val nameToken = consume(ID, "Expected method name")
        val name = nameToken.lexeme

        consume(LEFT_PAREN, "Expected '('")
        val parameters = parseParameters()
        consume(RIGHT_PAREN, "Expected ')'")

        var returnType: Type = Type.UnitType
        if (match(COLON)) {
            returnType = parseType()
        }

        val isAbstract = "abstract" in modifiers
        val isOverride = "override" in modifiers

        val body = if (isAbstract) {
            consume(SEMICOLON, "Expected ';' for abstract method")
            emptyList()
        } else {
            block()
        }

        return Method(name, parameters, returnType, body, isOverride, isAbstract, visibility, position = nameToken.position())
    }

    private fun interfaceDeclaration(): Stmt {
        val interfaceToken = consume(ID, "Expected interface name")
        val name = interfaceToken.lexeme

        consume(LBRACE, "Expected '{' at start of interface body")

        val methods = mutableListOf<Method>()

        while (!check(RBRACE) && !isAtEnd()) {
            if (match(FUN)) {
                methods.add(methodDeclaration())
            } else {
                error("Expected method declaration in interface", peek())
            }
        }

        consume(RBRACE, "Expected '}' at end of interface body")

        return Stmt.Interface(name, methods, position = interfaceToken.position())
    }

    private fun valDeclaration(): Stmt {
        val nameToken = consume(ID, "Expected constant name")
        val name = nameToken.lexeme
        val type = if (match(COLON)) {
            parseType()
        } else {
            Type.AnyType
        }
        consume(EQUALS, "Expected '=' after type")
        val initializer = expression()
        consumeOptional(SEMICOLON)
        return Stmt.Var(name, type, initializer, isMutable = false, position = nameToken.position())
    }

    private fun functionDeclaration(): Stmt {
        val nameToken = consume(ID, "Expected function name")
        val name = nameToken.lexeme
        consume(LEFT_PAREN, "Expected '(' after function name")

        val parameters = mutableListOf<Parameter>()
        if (!check(RIGHT_PAREN)) {
            do {
                val paramName = consume(ID, "Expected parameter name").lexeme
                consume(COLON, "Expected ':' after parameter name")
                val paramType = parseType()
                parameters.add(Parameter(paramName, paramType))
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expected ')' after parameters")

        consume(COLON, "Expected ':' before return type")
        val returnType = parseType()

        val body = block()

        return Stmt.Function(name, parameters, returnType, body, position = nameToken.position())
    }


    private fun parseType(): Type {
        return parseTypeWithNullable()
    }

    private fun parseTypeWithNullable(): Type {
        val type = parseTypeWithArrays()
        if (match(NULLABLE)) {
            return Type.Nullable(type)
        }
        return type
    }

    private fun parseTypeWithArrays(): Type {
        val typeToken = consume(ID, "Expected type name")
        val typeName = typeToken.lexeme

        var currentType: Type = when (typeName) {
            "Byte" -> Type.ByteType
            "Short" -> Type.ShortType
            "Int" -> Type.IntType
            "Long" -> Type.LongType
            "Float" -> Type.FloatType
            "Double" -> Type.DoubleType
            "Char" -> Type.CharType
            "UByte" -> Type.UByteType
            "UShort" -> Type.UShortType
            "UInt" -> Type.UIntType
            "Nothing" -> Type.NothingType
            "Bool" -> Type.BoolType
            "String" -> Type.StringType
            "Unit" -> Type.UnitType
            "Any" -> Type.AnyType
            "Object" -> Type.ObjectType
            else -> Type.ClassType(typeName)
        }

        val isBaseNullable = match(NULLABLE)
        if (isBaseNullable) {
            currentType = Type.Nullable(currentType)
        }

        while (match(LBRACKET)) {
            consume(RBRACKET, "Expected ']' after array type")
            currentType = Type.ArrayType(currentType)
        }

        return currentType
    }

    private fun breakStatement(): Stmt {
        val breakToken = previous()
        consumeOptional(SEMICOLON)
        return Stmt.Break(position = breakToken.position())
    }

    private fun continueStatement(): Stmt {
        val continueToken = previous()
        consumeOptional(SEMICOLON)
        return Stmt.Continue(position = continueToken.position())
    }

    private fun passStatement(): Stmt {
        val passToken = previous()
        consumeOptional(SEMICOLON)
        return Stmt.Pass(position = passToken.position())
    }

    private fun shadowStatement(): Stmt {
        val shadowToken = previous()
        val varName = consume(ID, "Expected variable name").lexeme
        val body = block()  
        return Stmt.Shadow(varName, body, position = shadowToken.position())
    }

    private fun swapStatement(): Stmt {
        val startPos = peek().position()
        val targets = mutableListOf<String>()

        do {
            targets.add(consume(ID, "Expected variable name").lexeme)
        } while (match(COMMA))

        consume(EQUALS, "Expected '='")

        val sources = mutableListOf<Expr>()
        do {
            sources.add(expression())
        } while (match(COMMA))

        if (targets.size != sources.size) {
            error("Number of variables (${targets.size}) must match number of expressions (${sources.size})", peek())
        }

        consumeOptional(SEMICOLON)

        return Stmt.Swap(targets, sources, position = startPos)
    }

    private fun callStatement(): Stmt {
        val nameToken = consume(ID, "Expected function name")
        val name = nameToken.lexeme
        consume(LEFT_PAREN, "Expected '(' after function name")

        val arguments = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                arguments.add(expression())
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expected ')' after arguments")
        consumeOptional(SEMICOLON)

        return Stmt.Call(name, arguments, position = nameToken.position())
    }


    private fun statement(): Stmt {

        if (isAtEnd() || check(RBRACE)) {
            return Stmt.Block(emptyList(), position = Position(0, 0, ""))
        }

        if (match(IF)) return ifStatement()
        if (match(WHILE)) return whileStatement()

        if (match(RETURN)) return returnStatement()
        if (match(FOR)) return forStatement()
        if (match(BREAK)) return breakStatement()
        if (match(CONTINUE)) return continueStatement()
        if (match(PASS)) return passStatement()
        if (match(SHADOW)) return shadowStatement()

        if (check(ID) && peekNext().type == COMMA) {
            return swapStatement()
        }

        if (check(ID) && peekNext().type == EQUALS) {
            return assignment()
        }

        val savePos = current

        try {
            val left = expression()
            if (match(EQUALS)) {
                val right = expression()
                if (!isAtEnd()) consumeOptional(SEMICOLON)
                return when (left) {
                    is Expr.FieldAccess -> Stmt.AssignField(left, right, position = left.position)
                    is Expr.ArrayAccess -> Stmt.ArrayAssignment(left, right, position = left.position)
                    else -> throw RuntimeException("Invalid assignment target")
                }
            } else {

                current = savePos
            }
        } catch (e: Exception) {
            current = savePos
        }

        val expr = expression()
        if (!isAtEnd() && peek().type != EOF) {
            consumeOptional(SEMICOLON)
        }
        return Stmt.Expression(expr, position = expr.position)
    }


    private fun returnStatement(): Stmt {
        val returnToken = previous()
        var value: Expr? = null
        if (!check(SEMICOLON) && !check(RBRACE)) {
            value = expression()
        }
        consumeOptional(SEMICOLON)
        return Stmt.Return(value, position = returnToken.position())
    }

    private fun forStatement(): Stmt {
        val forToken = previous()
        consume(LEFT_PAREN, "Expected '(' after for")

        var isMutable = true
        var varName: String

        if (check(VAR) || check(VAL)) {
            if (match(VAR)) {
                isMutable = true
            } else if (match(VAL)) {
                isMutable = false
            }
            varName = consume(ID, "Expected variable name").lexeme
        } else {
            varName = consume(ID, "Expected variable name").lexeme
        }

        consume(IN, "Expected 'in'")
        val iterable = expression()
        consume(RIGHT_PAREN, "Expected ')'")

        val body = block()

        return Stmt.For(varName, isMutable, iterable, body, position = forToken.position())
    }

    private fun ifStatement(): Stmt {
        val ifToken = previous()  
        consume(LEFT_PAREN, "Expected '('")
        val condition = expression()
        consume(RIGHT_PAREN, "Expected ')'")

        val thenBranch = block()

        var elseBranch: List<Stmt>? = null
        if (match(ELSE)) {

            if (match(IF)) {  

                val elseIfStmt = ifStatement()
                elseBranch = listOf(elseIfStmt)
            } else {

                elseBranch = block()
            }
        }

        return Stmt.If(condition, thenBranch, elseBranch, position = ifToken.position())
    }

    private fun whileStatement(): Stmt {
        val whileToken = previous()
        consume(LEFT_PAREN, "Expected '('")
        val condition = expression()
        consume(RIGHT_PAREN, "Expected ')'")

        val body = block()

        return Stmt.While(condition, body, position = whileToken.position())
    }
    private fun assignment(): Stmt {
        val nameToken = consume(ID, "Expected variable name")
        val name = nameToken.lexeme
        consume(EQUALS, "Expected '=' after variable name")
        val value = expression()
        consumeOptional(SEMICOLON)
        return Stmt.Assignment(name, value, position = nameToken.position())
    }

    private fun range(): Expr {
        var expr = addition()

        if (match(DOT_DOT)) {
            val operator = previous()
            val end = addition()

            var step: Expr? = null
            var isInclusive = true

            if (match(STEP)) {
                step = addition()
            }

            expr = Expr.Range(expr, end, isInclusive, step, position = operator.position())
        } else if (match(UNTIL)) {
            val end = addition()

            var step: Expr? = null
            if (match(STEP)) {
                step = addition()
            }

            expr = Expr.Range(expr, end, false, step, position = expr.position)
        }

        return expr
    }


    private fun blockStatement(): Stmt {
        return Stmt.Block(block(), position = Position(0, 0, ""))
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        consume(LBRACE, "Expected '{' at start of block")
        while (!check(RBRACE) && !isAtEnd()) {
            statements.add(declaration())  
        }
        consume(RBRACE, "Expected '}' at end of block")
        return statements
    }


    private fun parseArrayLiteral(): Expr {
        val startToken = previous()
        val elements = mutableListOf<Expr>()

        if (!check(RBRACKET)) {
            do {
                elements.add(expression())
            } while (match(COMMA))
        }

        consume(RBRACKET, "Expected ']' after array elements")

        val elementType = if (elements.isNotEmpty()) {
            elements.first().type ?: Type.IntType
        } else {
            Type.IntType
        }

        val arrayType = Type.ArrayType(elementType)
        return Expr.ArrayLiteral(elements, arrayType, position = startToken.position())
    }


    private fun expression(): Expr {
        var expr = inExpression()
        while (match(OR)) {
            val operator = previous()
            val right = inExpression()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun or(): Expr {
        var expr = and()
        while (match(AND)) {
            val operator = previous()
            val right = and()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun and(): Expr {
        var expr = equality()
        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(EQUALS_EQUALS, BANG_EQUALS)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = addition()
        while (match(GREATER, GREATER_EQUALS, LESS, LESS_EQUALS)) {
            val operator = previous()
            val right = addition()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }

        if (match(IN)) {
            val inToken = previous()

            val range = parseRange()
            expr = Expr.InRange(expr, range, type = Type.BoolType, position = inToken.position())
        }

        return expr
    }

    private fun parseRange(): Expr.Range {
        val start = multiplication()

        val (end, isInclusive, step) = when {
            match(DOT_DOT) -> {
                val end = multiplication()
                var step: Expr? = null
                if (match(STEP)) {
                    step = multiplication()
                }
                Triple(end, true, step)
            }
            match(UNTIL) -> {
                val end = multiplication()
                var step: Expr? = null
                if (match(STEP)) {
                    step = multiplication()
                }
                Triple(end, false, step)
            }
            else -> error("Expected '..' or 'until'", peek())
        }

        return Expr.Range(start, end, isInclusive, step, type = null, position = start.position)
    }

    private fun parseRangeOrExpression(): Expr {

        val savePos = current
        try {

            val start = multiplication()
            if (match(DOT_DOT)) {
                val end = multiplication()
                var step: Expr? = null
                if (match(STEP)) {
                    step = multiplication()
                }
                return Expr.Range(start, end, true, step, type = null, position = start.position)
            } else if (match(UNTIL)) {
                val end = multiplication()
                var step: Expr? = null
                if (match(STEP)) {
                    step = multiplication()
                }
                return Expr.Range(start, end, false, step, type = null, position = start.position)
            } else {

                current = savePos
                return expression()
            }
        } catch (e: Exception) {
            current = savePos
            return expression()
        }
    }

    private fun inExpression(): Expr {
        var expr = comparison()
        while (match(IN)) {
            val inToken = previous()

            val right = parseRangeOrExpression()
            expr = Expr.InRange(expr, right as Expr.Range, type = Type.BoolType, position = inToken.position())
        }
        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()
        while (match(PLUS, MINUS)) {
            val operator = previous()
            val right = multiplication()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun multiplication(): Expr {
        var expr = unary()
        while (match(STAR, SLASH, MOD)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right, position = operator.position())
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right, position = operator.position())
        }
        return primary()
    }

    private fun primary(): Expr {
        var expr: Expr

        if (match(NEW)) {
            val newToken = previous()
            val className = consume(ID, "Expected class name").lexeme
            consume(LEFT_PAREN, "Expected '('")
            val arguments = mutableListOf<Expr>()
            if (!check(RIGHT_PAREN)) {
                do {
                    arguments.add(expression())
                } while (match(COMMA))
            }
            consume(RIGHT_PAREN, "Expected ')'")
            expr = Expr.New(className, arguments, type = Type.ClassType(className), position = newToken.position())
        } else if (match(THIS)) {
            val token = previous()
            expr = Expr.This(position = token.position())
        } else if (match(SUPER)) {
            val token = previous()
            expr = Expr.Super(position = token.position())
        } else if (match(NUMBER)) {
            val token = previous()
            val value = token.literal
            val type = when (value) {
                is Byte -> Type.ByteType
                is Short -> Type.ShortType
                is Int -> Type.IntType
                is Long -> Type.LongType
                is Float -> Type.FloatType
                is Double -> Type.DoubleType
                is UByte -> Type.UByteType
                is UShort -> Type.UShortType
                is UInt -> Type.UIntType
                else -> error("Unknown numeric type", token)
            }
            expr = Expr.Literal(value, type, position = token.position())
        } else if (match(TRUE)) {
            val token = previous()
            expr = Expr.Literal(true, Type.BoolType, position = token.position())
        } else if (match(FALSE)) {
            val token = previous()
            expr = Expr.Literal(false, Type.BoolType, position = token.position())
        } else if (match(NULL)) {
            val token = previous()
            expr = Expr.Literal(null, null, position = token.position())
        } else if (match(STRING)) {
            val token = previous()
            val value = token.literal as String
            expr = Expr.Literal(value, Type.StringType, position = token.position())
        } else if (match(ID)) {
            val token = previous()
            val name = token.lexeme

            if (match(LEFT_PAREN)) {
                val arguments = mutableListOf<Expr>()
                if (!check(RIGHT_PAREN)) {
                    do {
                        arguments.add(expression())
                    } while (match(COMMA))
                }
                consume(RIGHT_PAREN, "Expected ')' after arguments")
                expr = Expr.Call(name, arguments, position = token.position())
            } else {
                expr = Expr.Variable(name, position = token.position())
            }
        } else if (match(LEFT_PAREN)) {
            expr = expression()
            consume(RIGHT_PAREN, "Expected ')' after expression")
        } else if (match(LBRACKET)) {
            expr = parseArrayLiteral()
        } else {
            if (isAtEnd() || check(RBRACE) || check(RBRACKET)) {
                error("Unexpected end of input", peek())
            }
            error("Expected expression, got ${peek().type}", peek())
        }

        while (true) {
            if (match(DOT_DOT)) {
                val end = addition()
                var step: Expr? = null
                if (match(STEP)) {
                    step = addition()
                }
                expr = Expr.Range(expr, end, true, step, position = expr.position)
            } else if (match(UNTIL)) {
                val end = addition()
                var step: Expr? = null
                if (match(STEP)) {
                    step = addition()
                }
                expr = Expr.Range(expr, end, false, step, position = expr.position)
            } else if (match(LBRACKET)) {
                val index = expression()
                consume(RBRACKET, "Expected ']'")
                expr = Expr.ArrayAccess(expr, index, null, position = expr.position)
            } else if (match(DOT)) {
                val nameToken = consume(ID, "Expected field or method name")
                val name = nameToken.lexeme
                if (match(LEFT_PAREN)) {
                    val arguments = mutableListOf<Expr>()
                    if (!check(RIGHT_PAREN)) {
                        do {
                            arguments.add(expression())
                        } while (match(COMMA))
                    }
                    consume(RIGHT_PAREN, "Expected ')'")
                    expr = Expr.MethodCall(expr, name, arguments, position = expr.position)
                } else {
                    expr = Expr.FieldAccess(expr, name, position = expr.position)
                }
            } else {
                break
            }
        }

        return expr
    }


    private fun peekNext(): Token {
        if (current + 1 >= tokens.size) return tokens[tokens.size - 1]
        return tokens[current + 1]
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }
        error(message, peek())
    }

    private fun consumeOptional(type: TokenType) {
        if (check(type)) {
            advance()
        }

    }
    private fun check(type: TokenType) = if (isAtEnd()) false else peek().type == type
    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }
    private fun isAtEnd() = peek().type == EOF
    private fun peek() = tokens[current]
    private fun previous() = tokens[current - 1]


    private fun error(message: String, token: Token): Nothing {
        throw ParserError(
            Position(
                line = token.line,
                column = token.column,
                lineContent = token.lineContent
            ),
            message
        )
    }

    private fun error(message: String): Nothing {
        val token = peek()
        throw ParserError(
            Position(
                line = token.line,
                column = token.column,
                lineContent = token.lineContent
            ),
            message
        )
    }
}