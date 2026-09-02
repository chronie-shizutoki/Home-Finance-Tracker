package com.chronie.homemoney.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Locks the JNI contract between [NativeSyncEngine] and `native-lib.cpp`.
 *
 * This boundary has a nasty failure mode: it is resolved by *name and descriptor string* at
 * runtime, so renaming a Kotlin method, reordering a parameter or changing `Int` to `Long`
 * compiles perfectly and links perfectly. The only symptom is a line in logcat
 * ("handleIncomingFrame is missing") and a server that refuses every v2 frame while still
 * answering v1 - which looks exactly like "the peer is too old", and would cost an afternoon
 * to track down.
 *
 * So the test reads the actual C++ source rather than a copy of its expectations:
 *
 *  - every `GetMethodID(...)` signature literal must equal the JVM descriptor computed by
 *    reflection from the Kotlin method of that name;
 *  - every exported `Java_..._<name>` symbol must have a matching `external fun`.
 *
 * Comparing against the C++ file is what makes this worth writing. Asserting the descriptor
 * against a hard-coded string here would only prove that this test agrees with itself.
 */
class NativeSyncEngineJniContractTest {

    private val nativeSource: String by lazy { locateNativeSource().readText() }

    // ------------------------------------------------------------------ upcalls

    @Test
    fun `every GetMethodID in native-lib matches the Kotlin method it resolves`() {
        val bindings = bindings()

        // Pinned rather than merely non-empty. A regex that silently stopped matching the
        // multi-line call would still "pass" a loop over one binding, and the upcall it
        // stopped covering is exactly the one worth covering.
        assertEquals(
            "the set of upcalls native resolves has changed",
            setOf("handleIncomingFrame"),
            bindings.keys
        )

        for ((name, signature) in bindings) {
            val method = NativeSyncEngine::class.java.declaredMethods.singleOrNull { it.name == name }
                ?: throw AssertionError(
                    "native-lib.cpp resolves '$name' but NativeSyncEngine has no such method. " +
                            "Renaming an upcall does not break the build, only the runtime."
                )
            assertEquals(
                "descriptor mismatch for '$name': native asks for a different shape than Kotlin offers",
                signature,
                method.descriptor()
            )
        }
    }

    @Test
    fun `the v2 upcall is bound and takes the frame header fields`() {
        // The descriptor is spelled out once, on the opcode path that carries every v2
        // frame, so that a reader can see what the contract actually is without decoding
        // the reflection helper - and so that both sides changing together still trips.
        assertEquals(
            "native-lib.cpp no longer asks for the v2 frame shape",
            "(Ljava/lang/String;IJI[B)[B",
            bindings()["handleIncomingFrame"]
        )

        val method = NativeSyncEngine::class.java.getDeclaredMethod(
            "handleIncomingFrame",
            String::class.java,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            ByteArray::class.java
        )
        assertEquals("(Ljava/lang/String;IJI[B)[B", method.descriptor())
    }


    // ------------------------------------------------------------------ downcalls

    @Test
    fun `every exported JNI symbol has an external fun`() {
        val exported = EXPORTED_SYMBOL.findAll(nativeSource)
            .map { it.groupValues[1] }
            .toSortedSet()

        assertTrue(
            "found no Java_com_chronie_..._NativeSyncEngine_ exports in native-lib.cpp",
            exported.isNotEmpty()
        )

        val declared = NativeSyncEngine::class.java.declaredMethods
            .filter { Modifier.isNative(it.modifiers) }
            .map { it.name }
            .toSortedSet()

        assertEquals(
            "native exports and Kotlin external declarations have drifted. " +
                    "A missing declaration is invisible until the method is called and " +
                    "throws UnsatisfiedLinkError.",
            exported,
            declared
        )
    }

    // ------------------------------------------------------------------ helpers

    /** Upcall name to signature literal, as native-lib.cpp actually spells them. */
    private fun bindings(): Map<String, String> =
        GET_METHOD_ID.findAll(nativeSource)
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** JVM method descriptor, e.g. `(Ljava/lang/String;IJI[B)[B`. */
    private fun Method.descriptor(): String =
        parameterTypes.joinToString("", prefix = "(", postfix = ")") { it.descriptor() } +
                returnType.descriptor()

    private fun Class<*>.descriptor(): String = when {
        this == Void.TYPE -> "V"
        this == Boolean::class.javaPrimitiveType -> "Z"
        this == Byte::class.javaPrimitiveType -> "B"
        this == Char::class.javaPrimitiveType -> "C"
        this == Short::class.javaPrimitiveType -> "S"
        this == Int::class.javaPrimitiveType -> "I"
        this == Long::class.javaPrimitiveType -> "J"
        this == Float::class.javaPrimitiveType -> "F"
        this == Double::class.javaPrimitiveType -> "D"
        isArray -> "[" + componentType.descriptor()
        else -> "L" + name.replace('.', '/') + ";"
    }

    /**
     * Unit tests run with the module directory as the working directory, but resolving
     * upwards as well keeps the test green when it is launched from the IDE with a
     * different root.
     */
    private fun locateNativeSource(): File {
        val relative = "app/src/main/cpp/native-lib.cpp"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, relative), File(dir, relative.removePrefix("app/")))) {
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File(".").absolutePath}")
    }

    private companion object {
        /** `GetMethodID(cls, "name", "(sig)ret")`, tolerating line breaks between arguments. */
        val GET_METHOD_ID = Regex("""GetMethodID\([^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)

        val EXPORTED_SYMBOL = Regex("""Java_com_chronie_homemoney_data_sync_NativeSyncEngine_(\w+)\s*\(""")
    }
}
