package com.threadmap.intellij.psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NodePolicyTest {
    private fun t(
        fqn: String? = "com.ae.x.Svc", inBase: Boolean = true, iface: Boolean = false,
        comp: Boolean = false, repoPort: Boolean = false, getter: Boolean = false, priv: Boolean = false,
    ) = CallTarget(fqn, inBase, iface, comp, repoPort, getter, priv)

    @Test fun `out of scope is skipped`() {
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(fqn = "java.lang.String", inBase = false)))
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(fqn = null, inBase = false)))
    }
    @Test fun `repository or port is a leaf`() =
        assertEquals(NodeDecision.LEAF, NodePolicy.decide(t(repoPort = true)))
    @Test fun `component method is an include node`() =
        assertEquals(NodeDecision.INCLUDE, NodePolicy.decide(t(comp = true)))
    @Test fun `in-scope interface is an include node (resolved later)`() =
        assertEquals(NodeDecision.INCLUDE, NodePolicy.decide(t(iface = true)))
    @Test fun `own private helper is followed through`() =
        assertEquals(NodeDecision.FOLLOW_THROUGH, NodePolicy.decide(t(priv = true)))
    @Test fun `getter setter builder is skipped`() =
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(getter = true)))
    @Test fun `in-scope plain method is followed through to find component calls`() =
        assertEquals(NodeDecision.FOLLOW_THROUGH, NodePolicy.decide(t()))
}
