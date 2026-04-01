# Aevum Programming Language

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)

Aevum is a statically typed, compiled programming language with modern features including object-oriented programming, nullable types, and unique language constructs.

## ✨ Features

### Core Language
- **Static typing** with type inference
- **Nullable types** (`String?`) for safer code
- **Unsigned integers** (`UByte`, `UShort`, `UInt`)
- **Classes and inheritance** with `extends` and `override`

### Unique Features
- **`shadow`** – Temporarily shadow variables with automatic restoration

### Control Flow
- `if` / `else if` / `else` with required braces
- `while` loops
- `for` loops over ranges, arrays, and strings
- `break` and `pass` statements

### Built-in Modules
- **`io`** – Input/output (`print`, `println`, `readln`, `readInt`)
- **`lang`** – Utilities (`typeOf`, `len`, `toString`, `toInt`, type checks)
- **`time`** – Time operations (`nowMillis`, `currentDate`, `currentTime`)

## 🎓 Educational Purpose

This project was created as a learning exercise to understand:

- **Lexical analysis** – Converting source code into tokens
- **Parsing** – Building Abstract Syntax Trees (AST)
- **Bytecode generation** – Compiling to custom VM instructions
- **Virtual Machine** – Implementing stack-based execution
- **Garbage collection concepts** – Object heap management
- **Type systems** – Static typing with nullable and unsigned types
- **OOP implementation** – Classes, inheritance, method dispatch


## 🚀 Quick Start

### Installation

```bash
git clone https://github.com/hksae/Aevum.git
cd Aevum
./gradlew build
```

### Run Your First Program

Create `hello.aevum`:

```aevum
println("Hello")

var x = 10
shadow x {
    x = 20
    println(x)
}
println(x)
```

Run it:

```bash
java -jar build/libs/aevum-1.0.0.jar hello.aevum
```

## 📖 Examples

### Classes and Inheritance

```aevum
class Animal(val name: String) {
    fun speak(): String = name + " says something"
}

class Dog(name: String) extends Animal(name) {
    override fun speak(): String = name + " barks"
}

var dog = Dog("Buddy")
println(dog.speak())  // Buddy barks
```

### Range Operations

```aevum
for (i in 1..5) {
    print(i + " ")  // 1 2 3 4 5
}

for (i in 0..10 step 2) {
    print(i + " ")  // 0 2 4 6 8 10
}
```

### Swap Operator

```aevum
var a = 10
var b = 20
a, b = b, a  // a = 20, b = 10
```

## 📚 Documentation

- [Language Syntax](docs/syntax.md)
- [Examples](examples/)
- [API Reference](docs/api.md)

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.