package com.threadmap.intellij.psi

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class StaticCallGraphWalkerTest : LightJavaCodeInsightFixtureTestCase() {

    private fun addBeans() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("""
            package com.ae.x;
            import org.springframework.stereotype.Service;
            @Service public class A {
              private final B b; private final C c;
              public A(B b, C c){ this.b=b; this.c=c; }
              public void entry(){ helper(); b.run(); }
              private void helper(){ c.calc(); "x".length(); }
            }""")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class B { public void run(){} }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class C { public void calc(){} }")
    }

    fun testWalksComponentCallsSkipsLibAndFollowsPrivateHelper() {
        addBeans()
        val a = myFixture.findClass("com.ae.x.A")
        val entry = a.findMethodsByName("entry", false).first()

        val root = StaticCallGraphWalker("com.ae.x").walk(entry)

        val childSigs = root.children.map { it.signature }
        assertTrue(childSigs.any { it.contains("B#run") })
        assertTrue(childSigs.any { it.contains("C#calc") })
        assertEquals(2, root.children.size)
        assertTrue(root.signature.contains("A#entry"))
    }
}
