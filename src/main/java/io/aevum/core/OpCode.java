package io.aevum.core;

public final class OpCode {
    private OpCode() {}

    public static final byte HALT  = 0;
    public static final byte CONST = 1;
    public static final byte ADD   = 2;
    public static final byte SUB   = 3;
    public static final byte MUL   = 4;
    public static final byte DIV   = 5;
    public static final byte SAVE  = 6;
    public static final byte LOAD  = 7;
    public static final byte PRINT = 8;
    public static final byte EQUAL = 9;
    public static final byte GREATER = 10;
    public static final byte JUMP_IF_FALSE = 11;
    public static final byte JUMP = 12;
    public static final byte LOOP = 13;
    public static final byte LESS = 14;
    public static final byte GREATER_EQUAL = 15;
    public static final byte LESS_EQUAL = 16;
    public static final byte NEG = 17;
    public static final byte AND = 18;
    public static final byte OR = 19;
    public static final byte NOT = 20;
    public static final byte NEW_ARRAY = 21;
    public static final byte LOAD_ARRAY_ELEMENT = 22;
    public static final byte STORE_ARRAY_ELEMENT = 23;
    public static final byte POP = 26;
    public static final byte DUP = 27;
    public static final byte CALL = 28;
    public static final byte RET = 29;
    public static final byte NEW = 30;
    public static final byte GET_FIELD = 31;
    public static final byte SET_FIELD = 32;
    public static final byte INVOKE_VIRTUAL = 33;
    public static final byte REGISTER_CLASS = 34;
    public static final byte LOAD_THIS = 35;
    public static final byte LOAD_SUPER = 36;
    public static final byte SAVE_TEMP = 37;
    public static final byte LOAD_TEMP = 38;
    public static final byte INVOKE_SUPER = 39;
    public static final byte CALL_BUILTIN = 40;
    public static final byte CALL_BY_INDEX = 41;
    public static final byte DUP_X2 = 42;
    public static final byte DUP_X1 = 43;
    public static final byte MOD = 44;
    public static final byte SHADOW_SAVE = 45;
    public static final byte SHADOW_RESTORE = 46;
}