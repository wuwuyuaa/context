package com.threadmap.core.annotate;

import java.io.IOException;
import java.util.Objects;

/** M2 处理流水:trace.json → 折叠 → 逐节点标注(懒标)→ AnnotatedTree。 */
public class AnnotationPipeline {
    private final TraceJsonParser parser = new TraceJsonParser();
    private final PackageFolder folder;
    private final Annotator annotator;
    private final AnnotationRequestBuilder requestBuilder;

    public AnnotationPipeline(PackageFolder folder, Annotator annotator) {
        this(folder, annotator, node -> AnnotationRequest.ofSignature(node.getSignature()));
    }

    public AnnotationPipeline(PackageFolder folder, Annotator annotator,
                              AnnotationRequestBuilder requestBuilder) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.annotator = Objects.requireNonNull(annotator, "annotator");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder");
    }

    public AnnotatedTree run(String traceJson) throws IOException {
        AnnotatedTree tree = parser.parse(traceJson);
        folder.fold(tree);
        annotate(tree.getRoot());
        return tree;
    }

    private void annotate(AnnotatedNode node) {
        if (!node.isCollapsed()) {
            node.setAnnotation(annotator.annotate(requestBuilder.build(node)));
        }
        for (AnnotatedNode child : node.getChildren()) {
            annotate(child);
        }
    }
}
