package com.threadmap.core.annotate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 端到端入口:trace.json → 折叠 + 标注 → .threadmap/annotated-tree.json。
 * run(...) 可测(注入 pipeline);main(...) 组装真实 Qwen 链(读 DASHSCOPE_API_KEY)。
 */
public final class ThreadmapCli {

    private ThreadmapCli() {
    }

    /** 可测核心:读 trace、跑 pipeline、写 annotated-tree。 */
    public static void run(Path traceJson, Path outputJson, AnnotationPipeline pipeline) throws IOException {
        String trace = Files.readString(traceJson);
        AnnotatedTree tree = pipeline.run(trace);
        if (outputJson.getParent() != null) {
            Files.createDirectories(outputJson.getParent());
        }
        Files.writeString(outputJson, new AnnotatedTreeJsonWriter().toJson(tree));
    }

    /**
     * 用法: ThreadmapCli &lt;trace.json&gt; &lt;out.json&gt; &lt;includePackage&gt; [sourceRoot...]
     * 读环境变量 DASHSCOPE_API_KEY;无 key 时退化为离线 FakeAnnotator。
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("用法: ThreadmapCli <trace.json> <out.json> <includePackage> [sourceRoot...]");
            System.exit(2);
            return;
        }
        Path trace = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String includePackage = args[2];

        List<Path> sourceRoots = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            sourceRoots.add(Path.of(args[i]));
        }

        Annotator annotator = buildAnnotator();
        AnnotationRequestBuilder requestBuilder = sourceRoots.isEmpty()
                ? node -> AnnotationRequest.ofSignature(node.getSignature())
                : new SourceBackedRequestBuilder(new MethodSourceExtractor(sourceRoots));

        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of(includePackage), 50),
                annotator,
                requestBuilder);

        run(trace, out, pipeline);
        System.out.println("已写出: " + out);
    }

    private static Annotator buildAnnotator() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("未设置 DASHSCOPE_API_KEY,使用离线 FakeAnnotator。");
            return new FakeAnnotator();
        }
        ChatFn chat = QwenChatModels.create(key, QwenChatModels.DEFAULT_MODEL);
        return new CachingAnnotator(new QwenAnnotator(chat, new FakeAnnotator()));
    }
}
