import io.aevum.compiler.CompileError
import io.aevum.compiler.Compiler
import io.aevum.compiler.Lexer
import io.aevum.compiler.Parser
import io.aevum.core.AevumVM
import java.io.File
import java.io.PrintStream

fun main(args: Array<String>) {
    System.setOut(PrintStream(System.out, true, "UTF-8"))

    if (args.isEmpty()) {
        println("Usage: java -jar aevum.jar <source.av>")
        return
    }

    val fileName = args[0]
    val source = File(fileName).readText(Charsets.UTF_8)
    runCode(source)
}

fun runCode(source: String) {
    try {
        val tokens = Lexer(source).scanTokens()
        val statements = Parser(tokens).parse()
        val compiler = Compiler()
        val (bytecode, constants) = compiler.compile(statements)
        val functionsTable = compiler.getFunctionsTable()
        val vm = AevumVM(bytecode, constants, functionsTable)
        vm.run()
    } catch (e: CompileError) {
        System.err.println(e.format())
    } catch (e: Exception) {
        System.err.println("\u001B[31mUnexpected error: ${e::class.simpleName}\u001B[0m")
        System.err.println(e.message)
    }
}