package com.threadmap.intellij.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceHashTest {

    @Test
    fun sameSourceSameHash() {
        assertEquals(SourceHash.of("void f(){ a(); }"), SourceHash.of("void f(){ a(); }"))
    }

    @Test
    fun differentSourceDifferentHash() {
        assertNotEquals(SourceHash.of("void f(){ a(); }"), SourceHash.of("void f(){ b(); }"))
    }

    @Test
    fun isShortStableHex() {
        assertEquals(16, SourceHash.of("anything").length)
    }
}
