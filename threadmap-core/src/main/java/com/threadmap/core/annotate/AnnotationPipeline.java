package com.threadmap.core.annotate;

import java.io.IOException;

/** M2 处理流水:trace.json → 折叠 → 逐节点标注(懒标)→ AnnotatedTree。 */
public class AnnotationPipeline {
    private final TraceJsonParser parser = new TraceJsonParser();
    private final PackageFolder folder;
    private final Annotator annotator;

    public AnnotationPipeline(PackageFolder folder, Annotator annotator) {
        this.folder = folder;
        this.annotator = annotator;
    }

    public AnnotatedTree run(String traceJson) throws IOException {
        AnnotatedTree tree = parser.parse(traceJson);
        folder.fold(tree);
        annotate(tree.getRoot());
        return tree;
    }

    private void annotate(AnnotatedNode node) {
        if (!node.isCollapsed()) {
            node.setAnnotation(annotator.annotate(
                    AnnotationRequest.ofSignature(node.getSignature())));
        }
        for (AnnotatedNode child : node.getChildren()) {
            annotate(child);
        }
    }
}
