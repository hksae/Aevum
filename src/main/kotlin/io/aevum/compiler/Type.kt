package io.aevum.compiler

sealed class Type {
    object ByteType : Type()
    object ShortType : Type()
    object IntType : Type()
    object LongType : Type()
    object FloatType : Type()
    object DoubleType : Type()
    object BoolType : Type()
    object StringType : Type()
    object UnitType : Type()
    object AnyType : Type()
    object ObjectType : Type()
    object CharType : Type()
    object UByteType : Type()
    object UShortType : Type()
    object UIntType : Type()
    object NothingType : Type()

    data class ClassType(val className: String) : Type() {
        override fun toString(): String = className
    }

    data class ArrayType(val elementType: Type) : Type() {
        override fun toString(): String = "${elementType}[]"
    }

    data class Nullable(val base: Type) : Type()

    fun widenTo(other: Type): Type {
        val order = listOf(ByteType, ShortType, IntType, LongType, FloatType, DoubleType)
        val thisIndex = order.indexOf(this)
        val otherIndex = order.indexOf(other)
        return if (thisIndex >= 0 && otherIndex >= 0) {
            order[maxOf(thisIndex, otherIndex)]
        } else {
            throw RuntimeException("Cannot widen $this and $other")
        }
    }

    fun isNumeric(): Boolean = this in listOf(ByteType, ShortType, IntType, LongType, FloatType, DoubleType)

    override fun toString(): String = when (this) {
        is ArrayType -> "${elementType}[]"
        ByteType -> "Byte"
        ShortType -> "Short"
        IntType -> "Int"
        LongType -> "Long"
        FloatType -> "Float"
        DoubleType -> "Double"
        CharType -> "Char"
        UByteType -> "UByte"
        UShortType -> "UShort"
        UIntType -> "UInt"
        NothingType -> "Nothing"
        BoolType -> "Bool"
        StringType -> "String"
        UnitType -> "Unit"
        AnyType -> "Any"
        ObjectType -> "Object"
        is ClassType -> className
        is Nullable -> "${base}?"
    }

    fun isNullable(): Boolean = this is Nullable

    fun canAssign(valueType: Type?): Boolean {
        return when {
            valueType == null && this.isNullable() -> true
            this == valueType -> true
            this == AnyType -> true
            valueType == AnyType -> true
            this.isNumeric() && valueType?.isNumeric() == true -> {
                val order = listOf(ByteType, ShortType, IntType, LongType, FloatType, DoubleType)
                val thisIndex = order.indexOf(this)
                val valueIndex = order.indexOf(valueType)
                valueIndex <= thisIndex
            }
            else -> false
        }
    }

    companion object {
        fun fromString(name: String, isNullable: Boolean = false): Type {
            val base = when (name) {
                "Byte" -> ByteType
                "Short" -> ShortType
                "Int" -> IntType
                "Long" -> LongType
                "Float" -> FloatType
                "Double" -> DoubleType
                "Bool" -> BoolType
                "String" -> StringType
                "Unit" -> UnitType
                "Any" -> AnyType
                "Object" -> ObjectType
                else -> throw RuntimeException("Unknown type: $name")
            }
            return if (isNullable) Nullable(base) else base
        }
    }
}