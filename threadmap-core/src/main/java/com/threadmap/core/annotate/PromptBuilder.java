package com.threadmap.core.annotate;

/** 按 spec §8 组装标注 prompt:只发方法源码 + 直接被调签名,要求严格 JSON、绑证据、禁空话。 */
public class PromptBuilder {

    private static final String INSTRUCTIONS = """
            你是资深 Java 工程师。仅根据下面给出的「方法签名 + 方法源码 + 直接被调方法签名」,
            用中文描述这个方法"做了什么"。必须绑定证据(落到行号/被调方法/副作用),禁止空话。

            严格只返回一个 JSON 对象(不要任何额外文字、不要 Markdown 围栏),字段如下:
            {
              "summary": "≤30字。说清这个方法做了什么**不显然**的事:点出关键分支/副作用/边界/前置条件。若方法名已说清其意图,就补名字看不出的要点。禁止复述方法名,禁止'负责/处理/执行核心业务逻辑'这类空话",
              "inputs": "关键入参(类型+含义),无则'无'",
              "outputs": "返回/产出;若主要是副作用(改了入参/落库),写清改了什么",
              "side_effects": ["仅当方法**自身确实**产生副作用时才填短标签:写库→'DB写'、调外部系统→'外部API'、发消息→'消息'、读写文件→'文件'。**绝大多数方法没有副作用,必须填空数组 []**。只凭源码里真实可见的调用判断;严禁照抄本说明里举的标签、严禁臆测"],
              "evidence": { "file": "源文件相对路径", "lines": "行号区间如 32-48", "calls": ["关键被调方法"] },
              "dig_worthy": false,
              "dig_reason": "dig_worthy 为 true 时给出原因;否则 null"
            }

            关于 dig_worthy(务必克制,宁缺毋滥——如果什么都标,就等于没标):
            默认 false。仅当满足下列**之一**才设 true:
            - 有不显然的副作用或外部依赖(落库/扣款/发消息/调外部系统),且影响正确性;
            - 有容易出错的分支、边界或异常处理(空值、并发、事务、超时、回退);
            - 涉及资金/库存/合规/权限等高风险语义;
            - 对调用方有隐藏契约或副作用(改了入参、依赖调用顺序)。
            纯转发、纯取值、纯映射、一目了然的方法**必须**设 false。一条链里通常只有少数节点 dig_worthy。
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
