package com.threadmap.intellij.psi

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class PsiMethodSourceTest : LightJavaCodeInsightFixtureTestCase() {

    fun testExtractsSourceAndCallees() {
        myFixture.addClass("package com.ae.x; public class Repo { public void save(){} }")
        myFixture.addClass("""
            package com.ae.x;
            public class Svc {
              private final Repo repo;
              public Svc(Repo repo){ this.repo = repo; }
              public void place(int qty){ validate(qty); repo.save(); }
              private void validate(int q){ }
            }""")
        val e = PsiMethodSource.extract(project, "com.ae.x.Svc#place(int)")
        assertNotNull(e)
        assertTrue("源码应含方法体,实际:" + e!!.source, e.source.contains("repo.save()"))
        assertTrue("被调名应含 save,实际:" + e.calleeNames, e.calleeNames.contains("save"))
        assertTrue("被调名应含 validate,实际:" + e.calleeNames, e.calleeNames.contains("validate"))
    }

    fun testReturnsNullWhenUnresolved() {
        assertNull(PsiMethodSource.extract(project, "com.nope.Missing#gone()"))
    }
}
