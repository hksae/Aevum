package io.aevum.compiler

class UByte(private val value: Byte) {
    fun getValue(): Byte = value
    override fun toString(): String = (value.toInt() and 0xFF).toString()
    override fun equals(other: Any?): Boolean = other is UByte && value == other.value
    override fun hashCode(): Int = value.toInt() and 0xFF
}

class UShort(private val value: Short) {
    fun getValue(): Short = value
    override fun toString(): String = (value.toInt() and 0xFFFF).toString()
    override fun equals(other: Any?): Boolean = other is UShort && value == other.value
    override fun hashCode(): Int = value.toInt() and 0xFFFF
}

class UInt(private val value: Int) {
    fun getValue(): Int = value
    override fun toString(): String = (value.toLong() and 0xFFFFFFFFL).toString()
    override fun equals(other: Any?): Boolean = other is UInt && value == other.value
    override fun hashCode(): Int = value
}

class ULong(private val value: java.math.BigInteger) {
    fun getValue(): java.math.BigInteger = value
    override fun toString(): String = value.toString()
    override fun equals(other: Any?): Boolean = other is ULong && value == other.value
    override fun hashCode(): Int = value.hashCode()
}