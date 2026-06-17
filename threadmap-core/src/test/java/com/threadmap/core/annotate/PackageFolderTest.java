package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PackageFolderTest {

    private static AnnotatedNode n(int id, String sig) {
        return new AnnotatedNode(id, sig, "f", 0, 1); // (id, sig, file, line, selfMs)
    }

    @Test
    void collapsesOutOfWhitelistButNotRootOrIncluded() {
        AnnotatedNode root = n(0, "com.example.Controller#handle()");
        AnnotatedNode svc = n(1, "com.example.Service#work()");
        AnnotatedNode lib = n(2, "org.springframework.Tx#run()");
        root.addChild(svc);
        svc.addChild(lib);
        AnnotatedTree tree = new AnnotatedTree("com.example.Controller#handle()", "t", root);

        new PackageFolder(List.of("com.example"), 10).fold(tree);

        assertFalse(root.isCollapsed(), "根永不折叠");
        assertFalse(svc.isCollapsed(), "白名单内不折叠");
        assertTrue(lib.isCollapsed(), "白名单外应折叠");
    }

    @Test
    void collapsesBeyondMaxDepth() {
        AnnotatedNode root = n(0, "com.example.A#a()");
        AnnotatedNode d1 = n(1, "com.example.B#b()");
        AnnotatedNode d2 = n(2, "com.example.C#c()");
        root.addChild(d1);
        d1.addChild(d2);
        AnnotatedTree tree = new AnnotatedTree("com.example.A#a()", "t", root);

        new PackageFolder(List.of("com.example"), 1).fold(tree);

        assertFalse(root.isCollapsed());  // depth 0
        assertFalse(d1.isCollapsed());    // depth 1 == maxDepth
        assertTrue(d2.isCollapsed());     // depth 2 > maxDepth
    }

    @Test
    void doesNotMatchLongerPackageName() {
        AnnotatedNode root = n(0, "com.example.Root#r()");
        AnnotatedNode impostor = n(1, "com.exampleother.Fake#f()");
        root.addChild(impostor);
        AnnotatedTree tree = new AnnotatedTree("com.example.Root#r()", "t", root);

        new PackageFolder(List.of("com.example"), 10).fold(tree);

        assertTrue(impostor.isCollapsed(), "com.exampleother 不应匹配 com.example 前缀");
    }

    @Test
    void emptyIncludePackagesCollapsesAllNonRoot() {
        AnnotatedNode root = n(0, "com.example.Root#r()");
        AnnotatedNode child = n(1, "com.example.Child#c()");
        root.addChild(child);
        AnnotatedTree tree = new AnnotatedTree("com.example.Root#r()", "t", root);

        new PackageFolder(List.of(), 10).fold(tree);

        assertFalse(root.isCollapsed(), "根永不折叠");
        assertTrue(child.isCollapsed(), "空白名单时非根全折叠");
    }
}
