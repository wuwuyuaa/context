package com.threadmap.intellij.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** 应用级设置:LLM 服务商(OpenAI 兼容)的 baseUrl / model + 安全库里的 API key。 */
@Service(Service.Level.APP)
@State(name = "ThreadmapSettings", storages = [Storage("threadmap.xml")])
class ThreadmapSettings : PersistentStateComponent<ThreadmapSettings.State> {

    data class State(
        var provider: String = DEFAULT_PROVIDER,
        var baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        var model: String = "qwen-flash",
    )

    private var myState = State()
    override fun getState(): State = myState
    override fun loadState(s: State) { myState = s }

    var provider: String
        get() = myState.provider
        set(v) { myState.provider = v }
    var baseUrl: String
        get() = myState.baseUrl
        set(v) { myState.baseUrl = v }
    var model: String
        get() = myState.model
        set(v) { myState.model = v }

    /** key 不进 State(不写明文 xml),走 IDE 安全库 PasswordSafe。 */
    private val credentials get() = CredentialAttributes(generateServiceName("脉络 Threadmap", "apiKey"))
    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentials) ?: ""
        set(v) { PasswordSafe.instance.setPassword(credentials, v.ifBlank { null }) }

    companion object {
        const val DEFAULT_PROVIDER = "通义千问(DashScope)"

        /** 预设:服务商 → (baseUrl, 默认 model)。多数是 OpenAI 兼容,只差这俩。"自定义" 留空自填。 */
        val PRESETS: Map<String, Pair<String, String>> = linkedMapOf(
            "通义千问(DashScope)" to ("https://dashscope.aliyuncs.com/compatible-mode/v1" to "qwen-flash"),
            "DeepSeek" to ("https://api.deepseek.com/v1" to "deepseek-chat"),
            "Kimi(Moonshot)" to ("https://api.moonshot.cn/v1" to "moonshot-v1-8k"),
            "智谱 GLM" to ("https://open.bigmodel.cn/api/paas/v4" to "glm-4-flash"),
            "OpenAI" to ("https://api.openai.com/v1" to "gpt-4o-mini"),
            "Ollama(本地)" to ("http://localhost:11434/v1" to "qwen2.5"),
            "自定义" to ("" to ""),
        )

        fun getInstance(): ThreadmapSettings =
            ApplicationManager.getApplication().getService(ThreadmapSettings::class.java)
    }
}
