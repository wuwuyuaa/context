package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilestonesTest {

    private AnnotatedNode n(int id, String sig) {
        return new AnnotatedNode(id, sig, "f", 0, 0);
    }

    @Test
    void recognizesMilestonesByClassSuffix() {
        assertTrue(Milestones.isMilestone("a.b.OrderRepository#save(O)"));
        assertTrue(Milestones.isMilestone("a.b.SellerCenterPort#fetch(S)"));
        assertTrue(Milestones.isMilestone("a.b.EventPublisher#publish(E)"));
        assertFalse(Milestones.isMilestone("a.b.OrderService#place(C)"));
        assertFalse(Milestones.isMilestone(null));
    }

    @Test
    void spineKeepsMilestonesAndAncestorsDropsPureBranches() {
        // root(controller) -> service -> { repo(里程碑), helper -> calc(纯枝节) }
        AnnotatedNode root = n(0, "a.Controller#h()");
        AnnotatedNode svc = n(1, "a.OrderService#place()");
        AnnotatedNode repo = n(2, "a.OrderRepository#save()");
        AnnotatedNode helper = n(3, "a.OrderService#validate()");
        AnnotatedNode calc = n(4, "a.PriceCalculator#calc()");
        svc.addChild(repo);
        svc.addChild(helper);
        helper.addChild(calc);
        root.addChild(svc);
        AnnotatedTree tree = new AnnotatedTree("a.Controller#h()", "t", root);

        Set<Integer> spine = Milestones.spineNodeIds(tree);
        assertEquals(Set.of(0, 1, 2), spine); // controller + service + repo;helper/calc 这条无里程碑被丢
    }
}
