package com.threadmap.intellij.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SignatureParserTest {

    @Test
    fun parsesFqcnMethodAndParamCount() {
        val p = SignatureParser.parse("com.ae.negotiation.X#check(RedlineCheckContext)")!!
        assertEquals("com.ae.negotiation.X", p.fqcn)
        assertEquals("check", p.methodName)
        assertEquals(1, p.paramCount)
    }

    @Test
    fun parsesNoArgsAndMultiArgs() {
        assertEquals(0, SignatureParser.parse("A#m()")!!.paramCount)
        assertEquals(2, SignatureParser.parse("A#m(String, int)")!!.paramCount)
    }

    @Test
    fun nestedClassKeepsBinaryNameInFqcn() {
        val p = SignatureParser.parse("com.ae.Outer\$Inner#run()")!!
        assertEquals("com.ae.Outer\$Inner", p.fqcn)
        assertEquals("run", p.methodName)
    }

    @Test
    fun returnsNullOnMalformed() {
        assertNull(SignatureParser.parse("garbage"))
        assertNull(SignatureParser.parse("NoHashMethod"))
    }
}
