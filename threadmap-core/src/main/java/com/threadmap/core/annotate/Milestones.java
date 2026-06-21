package com.threadmap.core.annotate;

import java.util.HashSet;
import java.util.Set;

/**
 * 主干判定:仅凭被调类名后缀认出"副作用边界"(仓储/外部/消息),作为主干里程碑。
 * 主干 = 里程碑 + 通往它们的祖先;用于"只标主干"——只对主干节点调 LLM,枝节留空。
 */
public final class Milestones {

    private Milestones() {
    }

    private static final String[] SUFFIXES = {
            "Repository", "Dao", "Port", "Client", "Gateway", "Feign",
            "Producer", "Publisher", "Sender",
    };

    public static boolean isMilestone(String signature) {
        if (signature == null) {
            return false;
        }
        int hash = signature.indexOf('#');
        String fqcn = hash < 0 ? signature : signature.substring(0, hash);
        String cls = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        for (String suffix : SUFFIXES) {
            if (cls.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** 应标注的主干节点 id 集合 = 里程碑节点 + 其所有祖先。 */
    public static Set<Integer> spineNodeIds(AnnotatedTree tree) {
        Set<Integer> ids = new HashSet<>();
        mark(tree.getRoot(), ids);
        return ids;
    }

    /** 子树含里程碑(或自身是)则把 node 计入主干,并向上传递。 */
    private static boolean mark(AnnotatedNode node, Set<Integer> ids) {
        boolean inSpine = isMilestone(node.getSignature());
        for (AnnotatedNode child : node.getChildren()) {
            if (mark(child, ids)) {
                inSpine = true;
            }
        }
        if (inSpine) {
            ids.add(node.getId());
        }
        return inSpine;
    }
}
