package io.aevum.compiler

import io.aevum.core.BuiltinFunction
import io.aevum.core.OpCode

class Compiler {
    private val scopes = mutableListOf<Scope>()
    private var currentScope: Scope
        get() = scopes.last()
        set(value) {
            scopes[scopes.size - 1] = value
        }

    private var nextVarIndex = 0
    private val bytecode = mutableListOf<Byte>()
    private val constants = mutableListOf<Any?>()
    private val functions = mutableMapOf<String, FunctionInfo>()
    private val classes = mutableMapOf<String, ClassInfo>()
    private var nextClassId = 0
    private var currentClass: ClassInfo? = null
    private var currentMethod: MethodInfo? = null
    private val moduleLoader = ModuleLoader()
    private val moduleConstants = mutableMapOf<String, Any?>()
    private val importedModules = mutableMapOf<String, Module>()
    private val moduleFunctions = mutableMapOf<String, BuiltinFunction>()
    private val functionList = mutableListOf<FunctionInfo>()
    private val loopVariableMapping = mutableMapOf<String, Int>()
    private val loopStack = mutableListOf<LoopInfo>()


    private data class LoopInfo(
        val startAddress: Int,
        var continueAddress: Int,   
        val breakAddresses: MutableList<Int>  
    ) {
        constructor(startAddress: Int) : this(startAddress, -1, mutableListOf())
    }

    init {
        scopes.add(Scope())
    }

    fun compile(statements: List<Stmt>): Pair<ByteArray, Array<Any?>> {
        val allStatements = mutableListOf<Stmt>()
        val ioModule = moduleLoader.loadModule("io", Position(0, 0, ""))
        for ((funcName, func) in ioModule.functions) {
            moduleFunctions[funcName] = func
            functions[funcName] = FunctionInfo(
                name = funcName,
                parameters = emptyList(),
                returnType = Type.AnyType,
                bytecodeStart = -1,
                localsSize = 0,
                isBuiltin = true
            )
        }
        for ((constName, value) in ioModule.constants) {
            moduleConstants[constName] = value
        }
        val langModule = moduleLoader.loadModule("lang", Position(0, 0, ""))
        for ((funcName, func) in langModule.functions) {
            moduleFunctions[funcName] = func
            functions[funcName] = FunctionInfo(
                name = funcName,
                parameters = emptyList(),
                returnType = Type.AnyType,
                bytecodeStart = -1,
                localsSize = 0,
                isBuiltin = true
            )
        }
        for ((constName, value) in langModule.constants) {
            moduleConstants[constName] = value
        }
        allStatements.addAll(statements)
        for (stmt in statements) {
            when (stmt) {
                is Stmt.Class -> {
                    registerClass(stmt)
                }
                is Stmt.Function -> {
                    val info = FunctionInfo(
                        name = stmt.name,
                        parameters = stmt.parameters,
                        returnType = stmt.returnType,
                        bytecodeStart = 0,
                        localsSize = 256
                    )
                    functions[stmt.name] = info
                    functionList.add(info)  
                }
                else -> {}
            }
        }

        for ((name, classInfo) in classes) {
            calculateFieldOffsets(classInfo)
            buildMethodTable(classInfo)
        }

        emitByte(OpCode.JUMP)
        val jumpAddr = bytecode.size
        emitInt(0)

        for (stmt in statements) {
            if (stmt is Stmt.Class) {
                compileClass(stmt)
            }
        }

        for (stmt in statements) {
            if (stmt is Stmt.Function) {
                val startAddress = bytecode.size
                val oldInfo = functions[stmt.name]!!
                val newInfo = oldInfo.copy(bytecodeStart = startAddress)
                functions[stmt.name] = newInfo

                val index = functionList.indexOf(oldInfo)
                if (index >= 0) {
                    functionList[index] = newInfo
                }

                compileFunctionBody(stmt)
            }
        }

        val mainStart = bytecode.size

        for (stmt in statements) {
            if (stmt !is Stmt.Function && stmt !is Stmt.Class && stmt !is Stmt.Interface) {
                genStmt(stmt)
            }
        }

        emitByte(OpCode.HALT)


        patchInt(jumpAddr, mainStart - (jumpAddr + 4))


        return Pair(bytecode.toByteArray(), constants.toTypedArray())
    }

    private fun pushScope() {
        scopes.add(Scope(currentScope))
    }

    private fun popScope() {
        scopes.removeAt(scopes.size - 1)
    }

    private fun error(position: Position, message: String): Nothing {
        throw CompilerError(position, message)
    }

    private fun typeError(position: Position, message: String): Nothing {
        throw TypeError(position, message)
    }

    private fun referenceError(position: Position, message: String): Nothing {
        throw ReferenceError(position, message)
    }

    private fun visibilityError(position: Position, message: String): Nothing {
        throw VisibilityError(position, message)
    }

    private fun compileRangeLoop(stmt: Stmt.For, range: Expr.Range) {

        val startVar = nextVarIndex
        val endVar = nextVarIndex + 1
        val stepVar = nextVarIndex + 2
        val currentVar = nextVarIndex + 3
        val elementVar = nextVarIndex + 4

        nextVarIndex += 5

        currentScope.define("__range_start_${stmt.variable}", Type.IntType, false, startVar)
        currentScope.define("__range_end_${stmt.variable}", Type.IntType, false, endVar)
        currentScope.define("__range_step_${stmt.variable}", Type.IntType, false, stepVar)
        currentScope.define("__range_current_${stmt.variable}", Type.IntType, true, currentVar)

        genExpr(range.start)
        emitByte(OpCode.SAVE)
        emitInt(startVar)

        genExpr(range.end)
        emitByte(OpCode.SAVE)
        emitInt(endVar)

        val step = range.step ?: Expr.Literal(1, Type.IntType, range.position)
        genExpr(step)
        emitByte(OpCode.SAVE)
        emitInt(stepVar)

        emitByte(OpCode.LOAD)
        emitInt(startVar)
        emitByte(OpCode.SAVE)
        emitInt(currentVar)

        val loopStart = bytecode.size

        emitByte(OpCode.LOAD)
        emitInt(currentVar)
        emitByte(OpCode.LOAD)
        emitInt(endVar)

        if (range.isInclusive) {
            emitByte(OpCode.LESS_EQUAL)
        } else {
            emitByte(OpCode.LESS)
        }

        emitByte(OpCode.JUMP_IF_FALSE)
        val exitJumpAddr = bytecode.size
        emitInt(0)  

        val loopInfo = LoopInfo(loopStart)
        loopStack.add(loopInfo)

        emitByte(OpCode.LOAD)
        emitInt(currentVar)
        emitByte(OpCode.SAVE)
        emitInt(elementVar)

        loopInfo.continueAddress = bytecode.size

        loopVariableMapping[stmt.variable] = elementVar

        for (s in stmt.body) {
            genStmt(s)
        }

        loopVariableMapping.remove(stmt.variable)

        emitByte(OpCode.LOAD)
        emitInt(currentVar)
        emitByte(OpCode.LOAD)
        emitInt(stepVar)
        emitByte(OpCode.ADD)
        emitByte(OpCode.SAVE)
        emitInt(currentVar)

        emitByte(OpCode.JUMP)
        val jumpBackAddr = bytecode.size
        emitInt(0)

        val jumpBackOffset = loopStart - (jumpBackAddr + 4)
        patchInt(jumpBackAddr, jumpBackOffset)

        val exitOffset = bytecode.size - (exitJumpAddr + 4)
        patchInt(exitJumpAddr, exitOffset)

        val endAddress = bytecode.size
        for (breakAddr in loopInfo.breakAddresses) {
            val offset = endAddress - (breakAddr + 5)  
            patchInt(breakAddr + 1, offset)  
        }

        loopStack.removeAt(loopStack.size - 1)
    }


    private fun registerClass(classDecl: Stmt.Class) {
        val methods = classDecl.methods.toMutableList()

        if (!methods.any { it.name == "<init>" }) {
            val defaultConstructor = Method(
                name = "<init>",
                parameters = emptyList(),
                returnType = Type.UnitType,
                body = emptyList(),
                isOverride = false,
                isAbstract = false,
                visibility = Visibility.PUBLIC,
                position = classDecl.position
            )
            methods.add(defaultConstructor)
        }

        classes[classDecl.name] = ClassInfo(
            name = classDecl.name,
            superClass = classDecl.superClass,
            fields = classDecl.fields,
            methods = methods,  
            classId = nextClassId++
        )
    }

    fun getFunctionsTable(): Array<FunctionInfo> {
        return functionList.toTypedArray()
    }

    private fun calculateFieldOffsets(classInfo: ClassInfo) {
        var offset = 0

        if (classInfo.superClass != null) {
            val superInfo = classes[classInfo.superClass]
            if (superInfo != null) {
                offset = superInfo.fieldCount
                classInfo.fieldOffsets.putAll(superInfo.fieldOffsets)

                classInfo.allFields.addAll(superInfo.allFields)
            }
        }

        for (field in classInfo.fields) {
            classInfo.fieldOffsets[field.name] = offset
            classInfo.allFields.add(field)
            offset++
        }
        classInfo.fieldCount = offset
    }

    private fun isSubclass(child: ClassInfo?, parent: ClassInfo): Boolean {
        if (child == null) return false
        if (child.name == parent.name) return true
        val superName = child.superClass ?: return false
        val superClass = classes[superName] ?: return false
        return isSubclass(superClass, parent)
    }

    private fun checkFieldAccess(classInfo: ClassInfo, fieldName: String, accessorClass: ClassInfo?): Boolean {
        val field = classInfo.allFields.find { it.name == fieldName } ?: return false

        return when (field.visibility) {
            Visibility.PUBLIC -> true
            Visibility.PRIVATE -> classInfo.name == accessorClass?.name
            Visibility.PROTECTED -> {
                if (accessorClass == null) return false
                if (classInfo.name == accessorClass.name) return true
                isSubclass(accessorClass, classInfo)
            }
        }
    }

    private fun checkMethodAccess(classInfo: ClassInfo, methodName: String, accessorClass: ClassInfo?): Boolean {
        val method = classInfo.methods.find { it.name == methodName } ?: return false

        return when (method.visibility) {
            Visibility.PUBLIC -> true
            Visibility.PRIVATE -> classInfo.name == accessorClass?.name
            Visibility.PROTECTED -> {
                if (accessorClass == null) return false
                if (classInfo.name == accessorClass.name) return true
                isSubclass(accessorClass, classInfo)
            }
        }
    }

    private fun buildMethodTable(classInfo: ClassInfo) {

        if (classInfo.superClass != null) {
            val superInfo = classes[classInfo.superClass]
            if (superInfo != null) {
                for ((name, address) in superInfo.methodTable) {
                    if (!classInfo.methodTable.containsKey(name)) {
                        classInfo.methodTable[name] = address  
                    }
                }
            }
        }

        for (method in classInfo.methods) {
            classInfo.methodTable[method.name] = 0
        }
    }

    private fun compileClass(classDecl: Stmt.Class) {
        currentClass = classes[classDecl.name]

        emitByte(OpCode.REGISTER_CLASS)
        emitInt(currentClass!!.classId)
        emitInt(currentClass!!.fieldCount)

        for (method in classDecl.methods) {
            compileMethod(method)
        }

        currentClass = null
    }

    private fun compileMethod(method: Method) {
        val startAddress = bytecode.size

        currentMethod = MethodInfo(
            name = method.name,
            parameters = method.parameters,
            returnType = method.returnType,
            bytecodeStart = startAddress,
            isOverride = method.isOverride,
            isAbstract = method.isAbstract
        )

        val oldNextVarIndex = nextVarIndex
        val oldScopes = scopes.toList()

        pushScope()

        currentScope.define("this", Type.ObjectType, false, 0)
        nextVarIndex = 1

        for (param in method.parameters) {
            currentScope.define(param.name, param.type, false, nextVarIndex)
            nextVarIndex++
        }

        if (currentClass != null) {
            for (field in currentClass!!.fields) {
                currentScope.defineField(field.name, field.type, field.isMutable)
            }
        }

        for (stmt in method.body) {
            genStmt(stmt)
        }

        if (method.body.lastOrNull() !is Stmt.Return) {
            if (method.name == "<init>") {
                emitByte(OpCode.RET)
            } else if (method.returnType == Type.UnitType) {
                emitByte(OpCode.CONST)
                val unitIndex = constants.size
                constants.add(Unit)
                emitInt(unitIndex)
                emitByte(OpCode.RET)
            } else {
                emitByte(OpCode.RET)
            }
        }

        popScope()
        nextVarIndex = oldNextVarIndex

        currentClass?.methodTable?.set(method.name, startAddress)

        currentMethod = null
    }

    private fun getClassOfExpr(expr: Expr): ClassInfo? {
        return when (expr) {
            is Expr.New -> classes[expr.className]
            is Expr.Variable -> {
                val symbol = currentScope.resolve(expr.name)
                when (val type = symbol?.type) {
                    is Type.ClassType -> classes[type.className]
                    is Type.Nullable -> {
                        when (val base = type.base) {
                            is Type.ClassType -> classes[base.className]
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            is Expr.FieldAccess -> getClassOfExpr(expr.objectExpr)
            is Expr.MethodCall -> getClassOfExpr(expr.objectExpr)
            is Expr.This -> currentClass
            is Expr.ArrayAccess -> {
                val elementType = getExpressionType(expr)
                when (elementType) {
                    is Type.ClassType -> classes[elementType.className]
                    is Type.Nullable -> {
                        when (val base = elementType.base) {
                            is Type.ClassType -> classes[base.className]
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun getExpressionType(expr: Expr): Type? {
        return when (expr) {
            is Expr.Literal -> expr.type
            is Expr.Variable -> {
                val symbol = currentScope.resolve(expr.name)
                symbol?.type
            }
            is Expr.Binary -> expr.type
            is Expr.Unary -> expr.type
            is Expr.ArrayLiteral -> expr.type
            is Expr.ArrayAccess -> {
                val arrayType = getExpressionType(expr.array)
                when (arrayType) {
                    is Type.ArrayType -> arrayType.elementType
                    is Type.Nullable -> {
                        if (arrayType.base is Type.ArrayType) {
                            (arrayType.base as Type.ArrayType).elementType
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
            is Expr.New -> expr.type
            is Expr.FieldAccess -> {
                val classInfo = getClassOfExpr(expr.objectExpr)
                val fieldType = classInfo?.fields?.find { it.name == expr.fieldName }?.type
                fieldType
            }
            is Expr.MethodCall -> {
                val classInfo = getClassOfExpr(expr.objectExpr)
                classInfo?.methods?.find { it.name == expr.methodName }?.returnType
            }
            is Expr.This -> currentClass?.let { Type.ClassType(it.name) }
            is Expr.Call -> {

                val func = functions[expr.name]
                func?.returnType
            }
            else -> null
        }
    }

    private fun getFieldOffset(classInfo: ClassInfo, fieldName: String, position: Position, accessorClass: ClassInfo?): Int {

        val ownerClass = findFieldOwner(classInfo, fieldName)
            ?: throw CompilerError(position, "Field '$fieldName' not found in class '${classInfo.name}'")

        if (!checkFieldAccess(ownerClass, fieldName, accessorClass)) {
            throw CompilerError(position, "Cannot access field '$fieldName' in class '${ownerClass.name}'")
        }

        return ownerClass.fieldOffsets[fieldName]!!
    }

    private fun findFieldOwner(classInfo: ClassInfo, fieldName: String): ClassInfo? {

        if (classInfo.allFields.any { it.name == fieldName }) {
            return classInfo
        }

        if (classInfo.superClass != null) {
            val superInfo = classes[classInfo.superClass]
            if (superInfo != null) {
                return findFieldOwner(superInfo, fieldName)
            }
        }

        return null
    }

    private fun getMethodAddress(classInfo: ClassInfo, methodName: String, position: Position, accessorClass: ClassInfo?): Int {

        if (!checkMethodAccess(classInfo, methodName, accessorClass)) {
            throw CompilerError(position, "Cannot access method '$methodName' in class '${classInfo.name}'")
        }

        classInfo.methodTable[methodName]?.let {
            if (it != 0) return it
        }

        if (classInfo.superClass != null) {
            val superInfo = classes[classInfo.superClass]
            if (superInfo != null) {
                return getMethodAddress(superInfo, methodName, position, accessorClass)
            }
        }

        throw CompilerError(position, "Method '$methodName' not found in class '${classInfo.name}'")
    }

    private fun compileCollectionLoop(stmt: Stmt.For, iterable: Expr) {
        val arrayVar = if (iterable is Expr.Variable) {
            val symbol = currentScope.resolve(iterable.name)
                ?: throw ReferenceError(iterable.position, "Unresolved reference: ${iterable.name}")
            symbol.index
        } else {
            val idx = nextVarIndex
            nextVarIndex++
            currentScope.define("__for_array_${stmt.variable}", Type.AnyType, false, idx)
            genExpr(iterable)
            emitByte(OpCode.SAVE)
            emitInt(idx)
            idx
        }

        val lengthVar = nextVarIndex
        nextVarIndex++
        val indexVar = nextVarIndex
        nextVarIndex++
        val elementVar = nextVarIndex
        nextVarIndex++

        currentScope.define("__for_length_${stmt.variable}", Type.IntType, false, lengthVar)
        currentScope.define("__for_index_${stmt.variable}", Type.IntType, true, indexVar)

        emitByte(OpCode.LOAD)
        emitInt(arrayVar)
        emitByte(OpCode.CALL_BUILTIN)
        val lenIdx = constants.size
        constants.add(moduleFunctions["len"]!!)
        emitInt(lenIdx)
        emitInt(1)
        emitByte(OpCode.SAVE)
        emitInt(lengthVar)

        emitByte(OpCode.CONST)
        val zeroIdx = constants.size
        constants.add(0)
        emitInt(zeroIdx)
        emitByte(OpCode.SAVE)
        emitInt(indexVar)

        val loopStart = bytecode.size

        emitByte(OpCode.LOAD)
        emitInt(indexVar)
        emitByte(OpCode.LOAD)
        emitInt(lengthVar)
        emitByte(OpCode.LESS)
        emitByte(OpCode.JUMP_IF_FALSE)
        val exitAddr = bytecode.size
        emitInt(0)  

        val loopInfo = LoopInfo(loopStart)
        loopStack.add(loopInfo)

        emitByte(OpCode.LOAD)
        emitInt(arrayVar)
        emitByte(OpCode.LOAD)
        emitInt(indexVar)
        emitByte(OpCode.LOAD_ARRAY_ELEMENT)

        emitByte(OpCode.SAVE)
        emitInt(elementVar)

        loopInfo.continueAddress = bytecode.size

        currentScope.define(stmt.variable, Type.AnyType, stmt.isMutable, elementVar)
        loopVariableMapping[stmt.variable] = elementVar

        for (s in stmt.body) {
            genStmt(s)
        }

        loopVariableMapping.remove(stmt.variable)

        emitByte(OpCode.LOAD)
        emitInt(indexVar)
        emitByte(OpCode.CONST)
        val oneIdx = constants.size
        constants.add(1)
        emitInt(oneIdx)
        emitByte(OpCode.ADD)
        emitByte(OpCode.SAVE)
        emitInt(indexVar)

        emitByte(OpCode.JUMP)
        val jumpAddr = bytecode.size
        emitInt(0)

        val jumpBackOffset = loopStart - (jumpAddr + 4)
        patchInt(jumpAddr, jumpBackOffset)

        val exitOffset = bytecode.size - (exitAddr + 4)
        patchInt(exitAddr, exitOffset)


        val endAddress = bytecode.size
        for (breakAddr in loopInfo.breakAddresses) {
            val offset = endAddress - (breakAddr + 5)  
            patchInt(breakAddr + 1, offset)  
        }

        loopStack.removeAt(loopStack.size - 1)
    }


    private fun compileFunctionBody(func: Stmt.Function) {
        val oldNextVarIndex = nextVarIndex

        scopes.add(Scope(currentScope))

        for ((index, param) in func.parameters.withIndex()) {
            currentScope.define(param.name, param.type, false, index)
        }
        nextVarIndex = func.parameters.size

        for (stmt in func.body) {
            genStmt(stmt)
        }

        if (func.body.lastOrNull() !is Stmt.Return) {
            if (func.returnType == Type.UnitType) {
                emitByte(OpCode.CONST)
                val unitIndex = constants.size
                constants.add(Unit)
                emitInt(unitIndex)
            }
            emitByte(OpCode.RET)
        }

        scopes.removeAt(scopes.size - 1)
        nextVarIndex = oldNextVarIndex
    }


    private fun genStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Swap -> {

                val tempVars = mutableListOf<Int>()
                for (i in stmt.sources.indices) {
                    val tempVar = nextVarIndex
                    nextVarIndex++
                    genExpr(stmt.sources[i])
                    emitByte(OpCode.SAVE)
                    emitInt(tempVar)
                    tempVars.add(tempVar)
                }

                for (i in stmt.targets.indices) {
                    val target = stmt.targets[i]
                    val tempVar = tempVars[i]

                    val symbol = currentScope.resolve(target)
                        ?: throw ReferenceError(stmt.position, "Unresolved reference: $target")

                    if (!symbol.isMutable) {
                        throw ReferenceError(stmt.position, "Val cannot be reassigned: $target")
                    }

                    emitByte(OpCode.LOAD)
                    emitInt(tempVar)
                    emitByte(OpCode.SAVE)
                    emitInt(symbol.index)
                }
            }
            is Stmt.Shadow -> {
                val originalSymbol = currentScope.resolve(stmt.variable)
                    ?: throw ReferenceError(stmt.position, "Unresolved reference: ${stmt.variable}")

                val savedVar = nextVarIndex
                nextVarIndex++
                emitByte(OpCode.LOAD)
                emitInt(originalSymbol.index)
                emitByte(OpCode.SAVE)
                emitInt(savedVar)

                val shadowVar = nextVarIndex
                nextVarIndex++
                emitByte(OpCode.LOAD)
                emitInt(originalSymbol.index)
                emitByte(OpCode.SAVE)
                emitInt(shadowVar)

                val oldMapping = loopVariableMapping[stmt.variable]
                loopVariableMapping[stmt.variable] = shadowVar

                for (s in stmt.body) {
                    genStmt(s)
                }

                if (oldMapping != null) {
                    loopVariableMapping[stmt.variable] = oldMapping
                } else {
                    loopVariableMapping.remove(stmt.variable)
                }

                emitByte(OpCode.LOAD)
                emitInt(savedVar)
                emitByte(OpCode.SAVE)
                emitInt(originalSymbol.index)
            }
            is Stmt.Expression -> {
                genExpr(stmt.expr)

            }
            is Stmt.Pass -> {

            }
            is Stmt.Break -> {
                if (loopStack.isEmpty()) {
                    throw CompilerError(stmt.position, "break outside of loop")
                }
                val loopInfo = loopStack.last()

                val breakAddr = bytecode.size
                emitByte(OpCode.JUMP)
                emitInt(0)  
                loopInfo.breakAddresses.add(breakAddr)
            }

            is Stmt.Continue -> {
                if (loopStack.isEmpty()) {
                    throw CompilerError(stmt.position, "continue outside of loop")
                }
                val loopInfo = loopStack.last()
                if (loopInfo.continueAddress == -1) {
                    throw CompilerError(stmt.position, "continue address not yet computed")
                }
                emitByte(OpCode.JUMP)
                emitInt(loopInfo.continueAddress - (bytecode.size + 4))
            }
            is Stmt.Import -> {
                val module = moduleLoader.loadModule(stmt.moduleName, stmt.position)
                val name = stmt.alias ?: stmt.moduleName
                importedModules[name] = module
                for ((funcName, func) in module.functions) {
                    moduleFunctions["$name.$funcName"] = func
                }
            }
            is Stmt.AssignField -> {
                when (val target = stmt.target) {
                    is Expr.FieldAccess -> {
                        val classInfo = getClassOfExpr(target.objectExpr)
                            ?: throw CompilerError(stmt.position, "Cannot determine class for field access")

                        val fieldOwner = findFieldOwner(classInfo, target.fieldName)
                            ?: throw CompilerError(stmt.position, "Field '${target.fieldName}' not found")

                        val field = fieldOwner.allFields.find { it.name == target.fieldName }

                        val isConstructor = currentMethod?.name == "<init>"

                        if (field != null && !field.isMutable && !isConstructor) {
                            throw CompilerError(stmt.position, "Val cannot be reassigned: ${target.fieldName}")
                        }

                        val fieldOffset = getFieldOffset(classInfo, target.fieldName, stmt.position, currentClass)

                        genExpr(target.objectExpr)
                        genExpr(stmt.value)
                        emitByte(OpCode.SET_FIELD)
                        emitInt(fieldOffset)
                    }
                    else -> throw CompilerError(stmt.position, "Invalid field assignment target")
                }
            }
            is Stmt.Function -> { }
            is Stmt.Class -> { }
            is Stmt.Interface -> { }
            is Stmt.Return -> {
                if (stmt.value != null) {
                    genExpr(stmt.value)
                } else {
                    emitByte(OpCode.CONST)
                    val unitIndex = constants.size
                    constants.add(Unit)
                    emitInt(unitIndex)
                }
                emitByte(OpCode.RET)
            }
            is Stmt.Call -> {
                val func = functions[stmt.name]
                    ?: throw ReferenceError(stmt.position, "Unresolved reference: ${stmt.name}")

                if (!func.isBuiltin && stmt.arguments.size != func.parameters.size) {
                    throw TypeError(stmt.position,
                        "Function '${stmt.name}' expects ${func.parameters.size} arguments, got ${stmt.arguments.size}")
                }

                for (arg in stmt.arguments) {
                    genExpr(arg)
                }

                if (func.isBuiltin) {
                    val funcIndex = constants.size
                    constants.add(moduleFunctions[stmt.name]!!)
                    emitByte(OpCode.CALL_BUILTIN)
                    emitInt(funcIndex)
                    emitInt(stmt.arguments.size)
                } else {
                    val funcIndex = functionList.indexOf(func)
                    emitByte(OpCode.CALL_BY_INDEX)
                    emitInt(funcIndex)
                    emitInt(func.localsSize)
                    emitInt(func.parameters.size)
                }

                if (func.returnType == Type.UnitType) {
                    emitByte(OpCode.POP)
                }
            }
            is Stmt.ArrayAssignment -> {
                when (val target = stmt.target) {
                    is Expr.ArrayAccess -> {
                        genExpr(target.array)
                        genExpr(target.index)
                        genExpr(stmt.value)
                        emitByte(OpCode.STORE_ARRAY_ELEMENT)
                    }
                    else -> throw CompilerError(stmt.position, "Invalid array assignment target")
                }
            }
            is Stmt.If -> {
                genExpr(stmt.condition)
                emitByte(OpCode.JUMP_IF_FALSE)
                val jumpToElseAddr = bytecode.size
                emitInt(0)

                for (s in stmt.thenBranch) genStmt(s)

                if (stmt.elseBranch != null) {
                    emitByte(OpCode.JUMP)
                    val jumpToEndAddr = bytecode.size
                    emitInt(0)

                    val startElse = bytecode.size
                    patchInt(jumpToElseAddr, startElse - (jumpToElseAddr + 4))

                    for (s in stmt.elseBranch) genStmt(s)

                    val endAll = bytecode.size
                    patchInt(jumpToEndAddr, endAll - (jumpToEndAddr + 4))
                } else {
                    val endThen = bytecode.size
                    patchInt(jumpToElseAddr, endThen - (jumpToElseAddr + 4))
                }
            }
            is Stmt.While -> {
                val loopStart = bytecode.size

                genExpr(stmt.condition)
                emitByte(OpCode.JUMP_IF_FALSE)
                val exitJumpAddr = bytecode.size
                emitInt(0)  

                val loopInfo = LoopInfo(loopStart)
                loopStack.add(loopInfo)

                loopInfo.continueAddress = bytecode.size

                for (s in stmt.body) {
                    genStmt(s)
                }

                emitByte(OpCode.JUMP)
                val jumpBackAddr = bytecode.size
                emitInt(0)

                val jumpBackOffset = loopStart - (jumpBackAddr + 4)
                patchInt(jumpBackAddr, jumpBackOffset)

                val exitOffset = bytecode.size - (exitJumpAddr + 4)
                patchInt(exitJumpAddr, exitOffset)


                val endAddress = bytecode.size
                for (breakAddr in loopInfo.breakAddresses) {
                    val offset = endAddress - (breakAddr + 5)  
                    patchInt(breakAddr + 1, offset)  
                }

                loopStack.removeAt(loopStack.size - 1)
            }
            is Stmt.Block -> {
                for (s in stmt.statements) {
                    genStmt(s)
                }
            }
            is Stmt.For -> {
                when (stmt.iterable) {
                    is Expr.Range -> {
                        compileRangeLoop(stmt, stmt.iterable)
                    }
                    else -> {
                        compileCollectionLoop(stmt, stmt.iterable)
                    }
                }
            }
            is Stmt.Var -> {

                val actualInitializer = if (stmt.type == Type.CharType) {
                    when (val init = stmt.initializer) {
                        is Expr.Literal -> {
                            when (val value = init.value) {
                                is String -> {
                                    if (value.length == 1) {
                                        Expr.Literal(value[0], Type.CharType, init.position)
                                    } else {
                                        throw TypeError(stmt.position, "Char literal must be a single character")
                                    }
                                }
                                else -> init
                            }
                        }
                        else -> stmt.initializer
                    }
                } else {
                    stmt.initializer
                }

                genExpr(actualInitializer)

                val actualType = if (stmt.type == Type.AnyType) {
                    actualInitializer.type ?: Type.AnyType
                } else {
                    stmt.type
                }

                val index = nextVarIndex
                val symbol = currentScope.define(stmt.name, actualType, stmt.isMutable, index)
                nextVarIndex++
                emitByte(OpCode.SAVE)
                emitInt(index)
            }
            is Stmt.Assignment -> {

                loopVariableMapping[stmt.name]?.let { varIndex ->

                    genExpr(stmt.value)
                    emitByte(OpCode.SAVE)
                    emitInt(varIndex)
                    return
                }

                val symbol = currentScope.resolve(stmt.name)
                if (symbol != null) {
                    if (!symbol.isMutable) {
                        throw ReferenceError(stmt.position, "Val cannot be reassigned: ${stmt.name}")
                    }
                    genExpr(stmt.value)
                    emitByte(OpCode.SAVE)
                    emitInt(symbol.index)
                    return
                }

                val field = currentScope.resolveField(stmt.name)
                if (field != null && currentClass != null) {
                    if (!field.isMutable) {
                        throw ReferenceError(stmt.position, "Val cannot be reassigned: ${stmt.name}")
                    }
                    emitByte(OpCode.LOAD_THIS)
                    genExpr(stmt.value)
                    val fieldOffset = getFieldOffset(currentClass!!, stmt.name, stmt.position, currentClass)
                    emitByte(OpCode.SET_FIELD)
                    emitInt(fieldOffset)
                    return
                }

                throw ReferenceError(stmt.position, "Unresolved reference: ${stmt.name}")
            }
        }
    }

    private fun genExpr(expr: Expr) {
        when (expr) {
            is Expr.InRange -> {

                genExpr(expr.value)

                val tempVar = nextVarIndex
                nextVarIndex++
                emitByte(OpCode.SAVE)
                emitInt(tempVar)

                val range = expr.range
                genExpr(range.start)
                emitByte(OpCode.SAVE)
                val startVar = nextVarIndex
                nextVarIndex++
                emitInt(startVar)

                genExpr(range.end)
                emitByte(OpCode.SAVE)
                val endVar = nextVarIndex
                nextVarIndex++
                emitInt(endVar)

                val stepVar = if (range.step != null) {
                    genExpr(range.step)
                    emitByte(OpCode.SAVE)
                    val stepIdx = nextVarIndex
                    nextVarIndex++
                    emitInt(stepIdx)
                    stepIdx
                } else {
                    -1
                }

                emitByte(OpCode.LOAD)
                emitInt(tempVar)
                emitByte(OpCode.LOAD)
                emitInt(startVar)
                emitByte(OpCode.GREATER_EQUAL)
                emitByte(OpCode.SAVE_TEMP)  

                emitByte(OpCode.LOAD)
                emitInt(tempVar)
                emitByte(OpCode.LOAD)
                emitInt(endVar)
                if (range.isInclusive) {
                    emitByte(OpCode.LESS_EQUAL)
                } else {
                    emitByte(OpCode.LESS)
                }

                emitByte(OpCode.LOAD_TEMP)  
                emitByte(OpCode.AND)        

                if (stepVar != -1) {
                    emitByte(OpCode.SAVE_TEMP)  

                    emitByte(OpCode.LOAD)
                    emitInt(tempVar)
                    emitByte(OpCode.LOAD)
                    emitInt(startVar)
                    emitByte(OpCode.SUB)
                    emitByte(OpCode.LOAD)
                    emitInt(stepVar)
                    emitByte(OpCode.MOD)
                    emitByte(OpCode.CONST)
                    val zeroIdx = constants.size
                    constants.add(0)
                    emitInt(zeroIdx)
                    emitByte(OpCode.EQUAL)

                    emitByte(OpCode.LOAD_TEMP)  
                    emitByte(OpCode.AND)        
                }
            }
            is Expr.Range -> {

                throw CompilerError(expr.position, "Range expression is only supported in for loops")
            }
            is Expr.Super -> {

                emitByte(OpCode.LOAD_THIS)

            }
            is Expr.Call -> {

                val classInfo = classes[expr.name]
                if (classInfo != null) {


                    emitByte(OpCode.NEW)
                    emitInt(classInfo.classId)
                    emitInt(classInfo.fieldCount)

                    emitByte(OpCode.SAVE_TEMP)

                    emitByte(OpCode.LOAD_TEMP)

                    for (arg in expr.arguments) {
                        genExpr(arg)
                    }

                    val constructorAddress = classInfo.methodTable["<init>"]
                    if (constructorAddress == null || constructorAddress == 0) {
                        throw CompilerError(expr.position, "Class ${expr.name} has no constructor")
                    }

                    emitByte(OpCode.INVOKE_VIRTUAL)
                    emitInt(constructorAddress)
                    emitInt(expr.arguments.size + 1)

                    emitByte(OpCode.LOAD_TEMP)
                    return
                }

                val func = functions[expr.name]
                    ?: throw ReferenceError(expr.position, "Unresolved reference: ${expr.name}")

                if (!func.isBuiltin && expr.arguments.size != func.parameters.size) {
                    throw TypeError(expr.position,
                        "Function '${expr.name}' expects ${func.parameters.size} arguments, got ${expr.arguments.size}")
                }

                for (arg in expr.arguments) {
                    genExpr(arg)
                }

                if (func.isBuiltin) {
                    val funcIndex = constants.size
                    constants.add(moduleFunctions[expr.name]!!)
                    emitByte(OpCode.CALL_BUILTIN)
                    emitInt(funcIndex)
                    emitInt(expr.arguments.size)
                } else {
                    val funcIndex = functionList.indexOf(func)
                    emitByte(OpCode.CALL_BY_INDEX)
                    emitInt(funcIndex)
                    emitInt(func.localsSize)
                    emitInt(func.parameters.size)
                }
            }
            is Expr.New -> {
                val classInfo = classes[expr.className]
                    ?: throw ReferenceError(expr.position, "Unknown class: ${expr.className}")

                emitByte(OpCode.NEW)
                emitInt(classInfo.classId)
                emitInt(classInfo.fieldCount)

                emitByte(OpCode.SAVE_TEMP)
                emitByte(OpCode.LOAD_TEMP)

                for (arg in expr.arguments) {
                    genExpr(arg)
                }

                val constructorAddress = classInfo.methodTable["<init>"]
                if (constructorAddress == null || constructorAddress == 0) {
                    throw CompilerError(expr.position, "Class ${expr.className} has no constructor")
                }

                emitByte(OpCode.INVOKE_VIRTUAL)
                emitInt(constructorAddress)
                emitInt(expr.arguments.size + 1)

                emitByte(OpCode.LOAD_TEMP)
            }
            is Expr.FieldAccess -> {
                genExpr(expr.objectExpr)

                val classInfo = getClassOfExpr(expr.objectExpr)
                    ?: throw CompilerError(expr.position, "Cannot determine class for field access")

                val fieldOffset = getFieldOffset(classInfo, expr.fieldName, expr.position, currentClass)

                emitByte(OpCode.GET_FIELD)
                emitInt(fieldOffset)
            }
            is Expr.MethodCall -> {
                if (expr.objectExpr is Expr.Variable) {
                    val moduleName = (expr.objectExpr as Expr.Variable).name
                    val fullFuncName = "$moduleName.${expr.methodName}"

                    val moduleFunc = moduleFunctions[fullFuncName]
                    if (moduleFunc != null) {
                        for (arg in expr.arguments) {
                            genExpr(arg)
                        }
                        val funcIndex = constants.size
                        constants.add(moduleFunc)
                        emitByte(OpCode.CALL_BUILTIN)
                        emitInt(funcIndex)
                        emitInt(expr.arguments.size)
                        return
                    }
                }
                if (expr.objectExpr is Expr.Super) {

                    val classInfo = currentClass!!
                    val superClassName = classInfo.superClass
                        ?: throw CompilerError(expr.position, "Class ${classInfo.name} has no superclass")

                    val superClass = classes[superClassName]
                        ?: throw CompilerError(expr.position, "Superclass $superClassName not found")

                    val methodAddress = getMethodAddress(superClass, expr.methodName, expr.position, currentClass)

                    emitByte(OpCode.LOAD_THIS)
                    for (arg in expr.arguments) {
                        genExpr(arg)
                    }
                    emitByte(OpCode.INVOKE_VIRTUAL)
                    emitInt(methodAddress)
                    emitInt(expr.arguments.size + 1)
                } else {

                    val classInfo = getClassOfExpr(expr.objectExpr)
                        ?: throw CompilerError(expr.position, "Cannot determine class for method call")

                    genExpr(expr.objectExpr)

                    for (arg in expr.arguments) {
                        genExpr(arg)
                    }

                    val methodAddress = getMethodAddress(classInfo, expr.methodName, expr.position, currentClass)

                    emitByte(OpCode.INVOKE_VIRTUAL)
                    emitInt(methodAddress)
                    emitInt(expr.arguments.size + 1)
                }
            }
            is Expr.This -> {
                emitByte(OpCode.LOAD_THIS)
            }
            is Expr.Super -> {
                emitByte(OpCode.LOAD_SUPER)
            }
            is Expr.ArrayLiteral -> {
                val size = expr.elements.size
                emitByte(OpCode.NEW_ARRAY)
                emitInt(size)

                expr.elements.forEachIndexed { index, element ->
                    emitByte(OpCode.DUP)
                    emitByte(OpCode.CONST)
                    val constIndex = constants.size
                    constants.add(index)
                    emitInt(constIndex)
                    genExpr(element)
                    emitByte(OpCode.STORE_ARRAY_ELEMENT)
                }
            }
            is Expr.ArrayAccess -> {
                genExpr(expr.array)
                genExpr(expr.index)

                val arrayType = getExpressionType(expr.array)

                val isValidIndex = when (arrayType) {
                    is Type.ArrayType -> true
                    is Type.StringType -> true  
                    is Type.Nullable -> {
                        when (arrayType.base) {
                            is Type.ArrayType -> true
                            is Type.StringType -> true
                            else -> false
                        }
                    }
                    else -> false
                }

                if (!isValidIndex) {
                    throw TypeError(expr.position, "Cannot index non-array/non-string type: $arrayType")
                }
                emitByte(OpCode.LOAD_ARRAY_ELEMENT)
            }
            is Expr.Literal -> {
                val index = constants.size
                when (val v = expr.value) {
                    null -> {
                        constants.add(null)
                        emitByte(OpCode.CONST)
                        emitInt(index)
                    }
                    is Byte, is Short, is Int, is Long, is Float, is Double,
                    is Boolean, is String, is Char, is java.math.BigInteger,
                    is UByte, is UShort, is UInt, is ULong -> {
                        constants.add(v)
                        emitByte(OpCode.CONST)
                        emitInt(index)
                    }
                    else -> throw CompilerError(expr.position, "Unknown literal type: ${v?.javaClass}")
                }
            }
            is Expr.Variable -> {

                loopVariableMapping[expr.name]?.let { varIndex ->
                    emitByte(OpCode.LOAD)
                    emitInt(varIndex)
                    return
                }

                val symbol = currentScope.resolve(expr.name)
                if (symbol != null) {
                    emitByte(OpCode.LOAD)
                    emitInt(symbol.index)
                    return
                }

                val field = currentScope.resolveField(expr.name)
                if (field != null && currentClass != null) {

                    emitByte(OpCode.LOAD_THIS)

                    val fieldOffset = getFieldOffset(currentClass!!, expr.name, expr.position, currentClass)
                    emitByte(OpCode.GET_FIELD)
                    emitInt(fieldOffset)
                    return
                }

                moduleConstants[expr.name]?.let {
                    emitByte(OpCode.CONST)
                    val constIndex = constants.size
                    constants.add(it)
                    emitInt(constIndex)
                    return
                }

                throw ReferenceError(expr.position, "Unresolved reference: ${expr.name}")
            }
            is Expr.Unary -> {
                genExpr(expr.right)
                when (expr.operator.type) {
                    TokenType.BANG -> emitByte(OpCode.NOT)
                    TokenType.MINUS -> emitByte(OpCode.NEG)
                    else -> throw CompilerError(expr.position, "Unknown unary operator: ${expr.operator.type}")
                }
            }
            is Expr.Binary -> {
                genExpr(expr.left)
                genExpr(expr.right)
                when (expr.operator.type) {
                    TokenType.PLUS -> emitByte(OpCode.ADD)
                    TokenType.MINUS -> emitByte(OpCode.SUB)
                    TokenType.STAR -> emitByte(OpCode.MUL)
                    TokenType.SLASH -> emitByte(OpCode.DIV)
                    TokenType.EQUALS_EQUALS -> emitByte(OpCode.EQUAL)
                    TokenType.BANG_EQUALS -> {
                        emitByte(OpCode.EQUAL)
                        emitByte(OpCode.NOT)
                    }
                    TokenType.GREATER -> emitByte(OpCode.GREATER)
                    TokenType.MOD -> emitByte(OpCode.MOD)
                    TokenType.GREATER_EQUALS -> emitByte(OpCode.GREATER_EQUAL)
                    TokenType.LESS -> emitByte(OpCode.LESS)
                    TokenType.LESS_EQUALS -> emitByte(OpCode.LESS_EQUAL)
                    TokenType.AND -> emitByte(OpCode.AND)
                    TokenType.OR -> emitByte(OpCode.OR)
                    else -> throw CompilerError(expr.position, "Unknown binary operator: ${expr.operator.type}")
                }
            }
        }
    }

    private fun emitByte(b: Byte) {
        bytecode.add(b)
    }

    private fun emitInt(value: Int) {
        bytecode.add((value shr 24).toByte())
        bytecode.add((value shr 16).toByte())
        bytecode.add((value shr 8).toByte())
        bytecode.add(value.toByte())
    }

    private fun patchInt(address: Int, value: Int) {
        bytecode[address] = (value shr 24).toByte()
        bytecode[address + 1] = (value shr 16).toByte()
        bytecode[address + 2] = (value shr 8).toByte()
        bytecode[address + 3] = value.toByte()
    }
}

data class FunctionInfo(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    var bytecodeStart: Int,
    var localsSize: Int,
    val isBuiltin: Boolean = false
)

data class ClassInfo(
    val name: String,
    val superClass: String?,
    val fields: List<Field>,
    val methods: List<Method>,
    val fieldOffsets: MutableMap<String, Int> = mutableMapOf(),
    val methodTable: MutableMap<String, Int> = mutableMapOf(),
    var classId: Int = -1,
    val allFields: MutableList<Field> = mutableListOf(),
    var fieldCount: Int = 0
)

data class MethodInfo(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val bytecodeStart: Int,
    val isOverride: Boolean,
    val isAbstract: Boolean
)
