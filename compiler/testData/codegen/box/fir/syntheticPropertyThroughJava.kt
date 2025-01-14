// TARGET_BACKEND: JVM_IR
// IGNORE_CODEGEN_WITH_IR_FAKE_OVERRIDE_GENERATION: KT-61370
// ISSUE: KT-59550

// FILE: Intermediate.java
public class Intermediate extends Base {
    public Intermediate(String foo) {
        super(foo);
    }
}

// FILE: FinalAndBase.kt
abstract class Base(private val foo: String) {
    fun getFoo() = foo
}

class Final(val i: Intermediate) : Intermediate(i.foo)

fun box(): String = Final(Intermediate("OK")).foo
