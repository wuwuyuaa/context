package com.threadmap.core.annotate;

import java.util.List;

/**
 * 折叠规则:根节点永不折叠;其余节点当深度超过 maxDepth 或声明类不在任一
 * 白名单包前缀下时,标记为 collapsed(自动折叠的工具/库节点)。
 */
public class PackageFolder {
    private final List<String> includePackages;
    private final int maxDepth;

    public PackageFolder(List<String> includePackages, int maxDepth) {
        this.includePackages = List.copyOf(includePackages);
        this.maxDepth = maxDepth;
    }

    public void fold(AnnotatedTree tree) {
        foldNode(tree.getRoot(), 0);
    }

    private void foldNode(AnnotatedNode node, int depth) {
        boolean collapse = depth > 0 && (depth > maxDepth || !included(node.getSignature()));
        node.setCollapsed(collapse);
        for (AnnotatedNode child : node.getChildren()) {
            foldNode(child, depth + 1);
        }
    }

    private boolean included(String signature) {
        int hash = signature.indexOf('#');
        String fqcn = hash >= 0 ? signature.substring(0, hash) : signature;
        for (String prefix : includePackages) {
            if (fqcn.startsWith(prefix + ".") || fqcn.equals(prefix)) {
                return true;
            }
        }
        return false;
    }
}
