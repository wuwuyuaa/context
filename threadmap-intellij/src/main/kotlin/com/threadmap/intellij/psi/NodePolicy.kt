package com.threadmap.intellij.psi

/** 静态走链对一个被调目标的取舍。 */
enum class NodeDecision { INCLUDE, LEAF, FOLLOW_THROUGH, SKIP }

/** 被调目标的判定输入(纯数据,便于不依赖 PSI 单测)。 */
data class CallTarget(
    val classFqn: String?,
    val inBasePackage: Boolean,
    val isInterface: Boolean,
    val hasComponentStereotype: Boolean,
    val isRepositoryOrPort: Boolean,
    val isGetterSetterOrBuilder: Boolean,
    val isOwnPrivateHelper: Boolean,
)

/** 取舍规则:Bean 级过滤,复刻动态 AOP 的边界。 */
object NodePolicy {
    fun decide(t: CallTarget): NodeDecision = when {
        t.classFqn == null || !t.inBasePackage -> NodeDecision.SKIP
        t.isRepositoryOrPort -> NodeDecision.LEAF
        t.hasComponentStereotype || t.isInterface -> NodeDecision.INCLUDE
        t.isOwnPrivateHelper -> NodeDecision.FOLLOW_THROUGH
        t.isGetterSetterOrBuilder -> NodeDecision.SKIP
        else -> NodeDecision.FOLLOW_THROUGH
    }
}
