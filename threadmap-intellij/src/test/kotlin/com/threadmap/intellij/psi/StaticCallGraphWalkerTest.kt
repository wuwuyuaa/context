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

    fun testResolvesSingleImplementationOfInterface() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package com.ae.x; public interface P { void go(); }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class PImpl implements P { public void go(){} }")
        myFixture.addClass("""
            package com.ae.x; import org.springframework.stereotype.Service;
            @Service public class U { private final P p; public U(P p){this.p=p;} public void run(){ p.go(); } }""")
        val u = myFixture.findClass("com.ae.x.U")
        val root = StaticCallGraphWalker("com.ae.x").walk(u.findMethodsByName("run", false).first())
        // p.go() 接口调用应解析到 PImpl#go,且标为「单实现推断」
        assertTrue(root.children.single().signature.contains("PImpl#go"))
        assertEquals("single_impl", root.children.single().confidence)
    }

    fun testMultipleImplementationsMarkedUncertain() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package com.ae.x; public interface P { void go(); }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class PImpl1 implements P { public void go(){} }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class PImpl2 implements P { public void go(){} }")
        myFixture.addClass("""
            package com.ae.x; import org.springframework.stereotype.Service;
            @Service public class U2 { private final P p; public U2(P p){this.p=p;} public void run(){ p.go(); } }""")
        val u = myFixture.findClass("com.ae.x.U2")
        val root = StaticCallGraphWalker("com.ae.x").walk(u.findMethodsByName("run", false).first())
        // 多实现:退回接口节点 P#go,标 multi_impl(不确定)
        val child = root.children.single()
        assertTrue(child.signature.contains("P#go"))
        assertEquals("multi_impl", child.confidence)
    }

    fun testConcreteCallIsCertain() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class D { public void calc(){} }")
        myFixture.addClass("""
            package com.ae.x; import org.springframework.stereotype.Service;
            @Service public class U3 { private final D d; public U3(D d){this.d=d;} public void run(){ d.calc(); } }""")
        val u = myFixture.findClass("com.ae.x.U3")
        val root = StaticCallGraphWalker("com.ae.x").walk(u.findMethodsByName("run", false).first())
        // 具体类方法直呼 = 确定,confidence 空
        assertEquals("", root.children.single().confidence)
    }

    fun testReadsSpringMarkersFromMethod() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package org.springframework.transaction.annotation; public @interface Transactional {}")
        myFixture.addClass("package org.springframework.scheduling.annotation; public @interface Async {}")
        myFixture.addClass("""
            package com.ae.x;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.scheduling.annotation.Async;
            @Service public class T {
              @Transactional @Async public void entry(){ }
            }""")
        val t = myFixture.findClass("com.ae.x.T")
        val root = StaticCallGraphWalker("com.ae.x").walk(t.findMethodsByName("entry", false).first())
        assertTrue("应读出事务,实际:" + root.markers, root.markers.contains("事务"))
        assertTrue("应读出异步,实际:" + root.markers, root.markers.contains("异步"))
    }

    fun testClassLevelTransactionalMarksMethod() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package org.springframework.transaction.annotation; public @interface Transactional {}")
        myFixture.addClass("""
            package com.ae.x;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            @Service @Transactional public class TC { public void entry(){ } }""")
        val tc = myFixture.findClass("com.ae.x.TC")
        val root = StaticCallGraphWalker("com.ae.x").walk(tc.findMethodsByName("entry", false).first())
        assertTrue("类级 @Transactional 应生效,实际:" + root.markers, root.markers.contains("事务"))
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
