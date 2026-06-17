package com.threadmap.core.annotate;

/** 按 spec §8 组装标注 prompt:只发方法源码 + 直接被调签名,要求严格 JSON、绑证据、禁空话。 */
public class PromptBuilder {

    private static final String INSTRUCTIONS = """
            你是资深 Java 工程师。仅根据下面给出的「方法签名 + 方法源码 + 直接被调方法签名」,
            用中文描述这个方法"做了什么"。必须绑定证据(落到行号/被调方法/副作用),禁止空话。

            严格只返回一个 JSON 对象(不要任何额外文字、不要 Markdown 围栏),字段如下:
            {
              "summary": "≤25字,点出改了什么状态/产出什么;禁止'负责核心业务逻辑'这类空话",
              "inputs": "关键入参",
              "outputs": "返回/产出",
              "side_effects": ["DB写 / 外部API / 消息 等;没有则空数组 []"],
              "evidence": { "file": "源文件相对路径", "lines": "行号区间如 32-48", "calls": ["关键被调方法"] },
              "dig_worthy": true,
              "dig_reason": "若 dig_worthy 为 true 给出原因;否则 null"
            }
            """;

    public String build(AnnotationRequest request) {
        String source = request.source() != null && !request.source().isBlank()
                ? request.source()
                : "(源码不可用,仅凭签名推断)";
        String callees = request.calleeSignatures().isEmpty()
                ? "(无)"
                : String.join("\n", request.calleeSignatures());

        return INSTRUCTIONS
                + "\n方法签名:\n" + request.signature()
                + "\n\n方法源码:\n" + source
                + "\n\n直接被调方法签名:\n" + callees + "\n";
    }
}
