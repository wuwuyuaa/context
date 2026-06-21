package com.threadmap.core.annotate;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/** M2 处理流水:trace.json → 折叠 → 逐节点标注(懒标)→ AnnotatedTree。 */
public class AnnotationPipeline {
    private final TraceJsonParser parser = new TraceJsonParser();
    private final PackageFolder folder;
    private final Annotator annotator;
    private final AnnotationRequestBuilder requestBuilder;
    private final boolean spineOnly;

    public AnnotationPipeline(PackageFolder folder, Annotator annotator) {
        this(folder, annotator, node -> AnnotationRequest.ofSignature(node.getSignature()));
    }

    public AnnotationPipeline(PackageFolder folder, Annotator annotator,
                              AnnotationRequestBuilder requestBuilder) {
        this(folder, annotator, requestBuilder, false);
    }

    /** spineOnly=true 时只标"主干"(里程碑 + 祖先),枝节留空,省 token。 */
    public AnnotationPipeline(PackageFolder folder, Annotator annotator,
                              AnnotationRequestBuilder requestBuilder, boolean spineOnly) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.annotator = Objects.requireNonNull(annotator, "annotator");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder");
        this.spineOnly = spineOnly;
    }

    public AnnotatedTree run(String traceJson) throws IOException {
        AnnotatedTree tree = parser.parse(traceJson);
        folder.fold(tree);
        Set<Integer> spine = spineOnly ? Milestones.spineNodeIds(tree) : null;
        annotate(tree.getRoot(), spine);
        return tree;
    }

    private void annotate(AnnotatedNode node, Set<Integer> spine) {
        if (!node.isCollapsed() && (spine == null || spine.contains(node.getId()))) {
            node.setAnnotation(annotator.annotate(requestBuilder.build(node)));
        }
        for (AnnotatedNode child : node.getChildren()) {
            annotate(child, spine);
        }
    }
}
