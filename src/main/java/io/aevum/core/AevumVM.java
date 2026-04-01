package io.aevum.core;

import io.aevum.compiler.FunctionInfo;

import java.util.*;

public class AevumVM {
    private final byte[] code;
    private final Object[] constants;
    private Object[] stack = new Object[65536];
    private int sp = 0;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int ip = 0;
    private final List<AevumObject> heap = new ArrayList<>();
    private final Map<Integer, ClassInfo> classTable = new HashMap<>();
    private Object tempValue = null;
    private Object[] argsBuffer = new Object[16];
    private final Deque<Object> shadowStack = new ArrayDeque<>();


    private record AevumObject(int classId, Object[] fields) {
        AevumObject(int classId, int fieldCount) {
            this(classId, new Object[fieldCount]);
        }
        @Override
        public String toString() {
            return "Object@" + Integer.toHexString(hashCode());
        }
    }

    private record ClassInfo(int classId, int fieldCount) {}

    private static final class Frame {
        final Object[] locals;
        final int returnAddress;
        Frame(int localsSize, int returnAddress) {
            this.locals = new Object[localsSize];
            this.returnAddress = returnAddress;
        }
    }

    private final FunctionInfo[] functionsTable;

    public AevumVM(byte[] code, Object[] constants, FunctionInfo[] functionsTable) {
        this.code = code;
        this.constants = constants;
        this.functionsTable = functionsTable;
        frames.push(new Frame(65536, -1));
    }

    private int readInt() {
        int b1 = code[ip++] & 0xFF;
        int b2 = code[ip++] & 0xFF;
        int b3 = code[ip++] & 0xFF;
        int b4 = code[ip++] & 0xFF;
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private void push(Object value) {
        if (sp == stack.length) {
            stack = Arrays.copyOf(stack, stack.length * 2);
        }
        stack[sp++] = value;
    }

    private Object pop() {
        if (sp == 0) throw new RuntimeException("Stack underflow");
        return stack[--sp];
    }

    private Object peek() {
        if (sp == 0) throw new RuntimeException("Stack is empty");
        return stack[sp - 1];
    }

    private boolean isTruthy(Object value) {
        return switch (value) {
            case Boolean b -> b;
            case Number n -> n.doubleValue() != 0;
            case String s -> !s.isEmpty();
            case AevumObject ignored -> true;
            case null -> false;
            default -> true;
        };
    }

    private int compareNumbers(Number a, Number b) {
        return Double.compare(a.doubleValue(), b.doubleValue());
    }

    private Number addNumbers(Number left, Number right) {
        if (left instanceof Double || right instanceof Double) return left.doubleValue() + right.doubleValue();
        if (left instanceof Float || right instanceof Float) return left.floatValue() + right.floatValue();
        if (left instanceof Long || right instanceof Long) return left.longValue() + right.longValue();
        return left.intValue() + right.intValue();
    }

    private Number subtractNumbers(Number left, Number right) {
        if (left instanceof Double || right instanceof Double) return left.doubleValue() - right.doubleValue();
        if (left instanceof Float || right instanceof Float) return left.floatValue() - right.floatValue();
        if (left instanceof Long || right instanceof Long) return left.longValue() - right.longValue();
        return left.intValue() - right.intValue();
    }

    private Number multiplyNumbers(Number left, Number right) {
        if (left instanceof Double || right instanceof Double) return left.doubleValue() * right.doubleValue();
        if (left instanceof Float || right instanceof Float) return left.floatValue() * right.floatValue();
        if (left instanceof Long || right instanceof Long) return left.longValue() * right.longValue();
        return left.intValue() * right.intValue();
    }

    private Number divideNumbers(Number left, Number right) {
        if (left instanceof Double || right instanceof Double) return left.doubleValue() / right.doubleValue();
        if (left instanceof Float || right instanceof Float) return left.floatValue() / right.floatValue();
        if (left instanceof Long || right instanceof Long) return left.longValue() / right.longValue();
        return left.intValue() / right.intValue();
    }

    public void run() {
        while (true) {
            byte instruction = code[ip++];
            switch (instruction) {
                case OpCode.HALT -> { return; }
                case OpCode.ADD -> {
                    Object right = pop();
                    Object left = pop();
                    if (left == null || right == null) {
                        throw new RuntimeException("Cannot add with null value");
                    }
                    if (left instanceof Number && right instanceof Number) {
                        push(addNumbers((Number) left, (Number) right));
                    } else if (left instanceof String || right instanceof String) {
                        push(left + String.valueOf(right));
                    } else {
                        throw new RuntimeException("Cannot add values of different types");
                    }
                }
                case OpCode.SUB -> {
                    Object right = pop();
                    Object left = pop();
                    if (left instanceof Number && right instanceof Number) {
                        push(subtractNumbers((Number) left, (Number) right));
                    } else {
                        throw new RuntimeException("Subtraction requires numbers");
                    }
                }
                case OpCode.MUL -> {
                    Object right = pop();
                    Object left = pop();
                    if (left instanceof Number && right instanceof Number) {
                        push(multiplyNumbers((Number) left, (Number) right));
                    } else {
                        throw new RuntimeException("Multiplication requires numbers");
                    }
                }
                case OpCode.SHADOW_SAVE -> {
                    shadowStack.push(pop());
                }
                case OpCode.SHADOW_RESTORE -> {
                    push(shadowStack.pop());
                }
                case OpCode.DIV -> {
                    Object right = pop();
                    Object left = pop();
                    if (left instanceof Number && right instanceof Number) {
                        double divisor = ((Number) right).doubleValue();
                        if (divisor == 0) {
                            throw new RuntimeException("Division by zero");
                        }
                        push(divideNumbers((Number) left, (Number) right));
                    } else {
                        throw new RuntimeException("Division requires numbers");
                    }
                }
                case OpCode.SAVE -> {
                    int index = readInt();
                    frames.getFirst().locals[index] = pop();
                }
                case OpCode.LOAD -> {
                    int index = readInt();
                    push(frames.getFirst().locals[index]);
                }
                case OpCode.CALL_BY_INDEX -> {
                    int funcIndex = readInt();
                    int localsSize = readInt();
                    int argCount = readInt();
                    int functionAddress = functionsTable[funcIndex].getBytecodeStart();
                    Frame newFrame = new Frame(localsSize, ip);
                    for (int i = argCount - 1; i >= 0; i--) {
                        newFrame.locals[i] = pop();
                    }
                    frames.push(newFrame);
                    ip = functionAddress;
                }
                case OpCode.CALL -> {
                    int functionAddress = readInt();
                    int localsSize = readInt();
                    int argCount = readInt();
                    Frame newFrame = new Frame(localsSize, ip);
                    for (int i = argCount - 1; i >= 0; i--) {
                        newFrame.locals[i] = pop();
                    }
                    frames.push(newFrame);
                    ip = functionAddress;
                }
                case OpCode.RET -> {
                    if (frames.size() <= 1) {
                        return;
                    }
                    Frame frame = frames.pop();
                    ip = frame.returnAddress;
                }
                case OpCode.EQUAL -> {
                    Object b = pop();
                    Object a = pop();
                    if (a == null && b == null) push(true);
                    else if (a == null || b == null) push(false);
                    else push(a.equals(b));
                }
                case OpCode.GREATER, OpCode.GREATER_EQUAL, OpCode.LESS, OpCode.LESS_EQUAL -> {
                    Object b = pop();
                    Object a = pop();
                    if (a instanceof Number && b instanceof Number) {
                        int cmp = compareNumbers((Number) a, (Number) b);
                        boolean result = switch (instruction) {
                            case OpCode.GREATER -> cmp > 0;
                            case OpCode.GREATER_EQUAL -> cmp >= 0;
                            case OpCode.LESS -> cmp < 0;
                            case OpCode.LESS_EQUAL -> cmp <= 0;
                            default -> false;
                        };
                        push(result);
                    } else {
                        throw new RuntimeException("Comparison requires numbers");
                    }
                }
                case OpCode.AND -> {
                    Object right = pop();
                    Object left = pop();
                    push(isTruthy(left) && isTruthy(right));
                }
                case OpCode.OR -> {
                    Object right = pop();
                    Object left = pop();
                    push(isTruthy(left) || isTruthy(right));
                }
                case OpCode.NOT -> {
                    Object value = pop();
                    push(!isTruthy(value));
                }
                case OpCode.JUMP_IF_FALSE -> {
                    int offset = readInt();
                    Object condition = pop();
                    if (!isTruthy(condition)) {
                        ip += offset;
                    }
                }
                case OpCode.JUMP -> {
                    int offset = readInt();
                    ip += offset;
                }
                case OpCode.LOOP -> {
                    int offset = readInt();
                    ip -= offset;
                }
                case OpCode.CONST -> {
                    int index = readInt();
                    push(constants[index]);
                }
                case OpCode.CALL_BUILTIN -> {
                    int funcIndex = readInt();
                    int argCount = readInt();
                    BuiltinFunction func = (BuiltinFunction) constants[funcIndex];
                    List<Object> args = new ArrayList<>();
                    for (int i = 0; i < argCount; i++) {
                        args.add(0, pop());
                    }
                    Object result = func.call(args);
                    if (result != null) push(result);
                }
                case OpCode.MOD -> {
                    Object right = pop();
                    Object left = pop();
                    if (left instanceof Number && right instanceof Number) {
                        double divisor = ((Number) right).doubleValue();
                        if (divisor == 0) {
                            throw new RuntimeException("Modulo by zero");
                        }
                        if (left instanceof Double || right instanceof Double) {
                            push(((Number) left).doubleValue() % ((Number) right).doubleValue());
                        } else if (left instanceof Float || right instanceof Float) {
                            push(((Number) left).floatValue() % ((Number) right).floatValue());
                        } else if (left instanceof Long || right instanceof Long) {
                            push(((Number) left).longValue() % ((Number) right).longValue());
                        } else {
                            push(((Number) left).intValue() % ((Number) right).intValue());
                        }
                    } else {
                        throw new RuntimeException("Modulo requires numbers");
                    }
                }
                case OpCode.NEG -> {
                    Object value = pop();
                    if (value instanceof Number num) {
                        if (num instanceof Double) push(-num.doubleValue());
                        else if (num instanceof Float) push(-num.floatValue());
                        else if (num instanceof Long) push(-num.longValue());
                        else push(-num.intValue());
                    } else {
                        throw new RuntimeException("Negation requires a number");
                    }
                }
                case OpCode.POP -> { pop(); }
                case OpCode.DUP -> { push(peek()); }
                case OpCode.NEW_ARRAY -> {
                    int size = readInt();
                    push(new Object[size]);
                }
                case OpCode.LOAD_ARRAY_ELEMENT -> {
                    int index = ((Number) pop()).intValue();
                    Object array = pop();

                    if (array instanceof Object[] arr) {
                        if (index < 0 || index >= arr.length) {
                            throw new RuntimeException("Array index out of bounds: " + index);
                        }
                        push(arr[index]);
                    } else if (array instanceof String str) {
                        if (index < 0 || index >= str.length()) {
                            throw new RuntimeException("String index out of bounds: " + index);
                        }
                        push(String.valueOf(str.charAt(index)));
                    } else {
                        throw new RuntimeException("Cannot index non-array/non-string type: " +
                                (array == null ? "null" : array.getClass().getSimpleName()));
                    }
                }
                case OpCode.STORE_ARRAY_ELEMENT -> {
                    Object value = pop();
                    int index = ((Number) pop()).intValue();
                    Object[] array = (Object[]) pop();
                    if (index < 0 || index >= array.length) {
                        throw new RuntimeException("Array index out of bounds: " + index);
                    }
                    array[index] = value;
                }
                case OpCode.REGISTER_CLASS -> {
                    int classId = readInt();
                    int fieldCount = readInt();
                    classTable.put(classId, new ClassInfo(classId, fieldCount));
                }
                case OpCode.NEW -> {
                    int classId = readInt();
                    int fieldCount = readInt();
                    push(heap.size());
                    heap.add(new AevumObject(classId, fieldCount));
                }
                case OpCode.DUP_X1 -> {
                    if (sp < 2) throw new RuntimeException("Stack underflow for DUP_X1");
                    Object v1 = stack[sp - 1];
                    Object v2 = stack[sp - 2];
                    push(v2);
                }

                case OpCode.DUP_X2 -> {
                    if (sp < 3) throw new RuntimeException("Stack underflow for DUP_X2");
                    Object v1 = stack[sp - 1];
                    Object v2 = stack[sp - 2];
                    Object v3 = stack[sp - 3];
                    push(v3);
                }
                case OpCode.GET_FIELD -> {
                    int fieldIndex = readInt();
                    int objRef = ((Number) pop()).intValue();
                    if (objRef < 0 || objRef >= heap.size()) {
                        throw new RuntimeException("Invalid object reference");
                    }
                    push(heap.get(objRef).fields()[fieldIndex]);
                }
                case OpCode.SET_FIELD -> {
                    Object value = pop();
                    int fieldIndex = readInt();
                    int objRef = ((Number) pop()).intValue();
                    if (objRef < 0 || objRef >= heap.size()) {
                        throw new RuntimeException("Invalid object reference");
                    }
                    heap.get(objRef).fields()[fieldIndex] = value;
                }
                case OpCode.INVOKE_VIRTUAL, OpCode.INVOKE_SUPER -> {
                    int methodAddress = readInt();
                    int argCount = readInt();
                    Frame newFrame = new Frame(argCount, ip);
                    for (int i = argCount - 1; i >= 0; i--) {
                        newFrame.locals[i] = pop();
                    }
                    frames.push(newFrame);
                    ip = methodAddress;
                }
                case OpCode.LOAD_THIS, OpCode.LOAD_SUPER -> { push(frames.getFirst().locals[0]); }
                case OpCode.SAVE_TEMP -> { tempValue = pop(); }
                case OpCode.LOAD_TEMP -> { push(tempValue); }
                default -> throw new RuntimeException("Unknown instruction: " + instruction);
            }
        }
    }
}