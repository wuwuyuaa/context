package com.threadmap.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** 设置页(Settings > Tools > 脉络 Threadmap):选服务商(自动填 baseUrl/model)+ 填 key(存安全库)。 */
class ThreadmapConfigurable : Configurable {

    private val providerCombo = ComboBox(ThreadmapSettings.PRESETS.keys.toTypedArray())
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val keyField = JBPasswordField()
    private var loading = false
    private var root: JComponent? = null

    override fun getDisplayName(): String = "脉络 Threadmap"

    override fun createComponent(): JComponent {
        // 选预设时自动填 baseUrl/model;reset 期间(loading)不触发,免得覆盖已保存的自定义值。
        providerCombo.addActionListener {
            if (loading) return@addActionListener
            val preset = ThreadmapSettings.PRESETS[providerCombo.selectedItem as? String]
            if (preset != null && preset.first.isNotEmpty()) {
                baseUrlField.text = preset.first
                modelField.text = preset.second
            }
        }
        reset()
        root = FormBuilder.createFormBuilder()
            .addLabeledComponent("服务商:", providerCombo)
            .addLabeledComponent("Base URL:", baseUrlField)
            .addLabeledComponent("模型:", modelField)
            .addLabeledComponent("API Key:", keyField)
            .addComponentToRightColumn(
                JBLabel("<html><small>Key 存于 IDE 安全库;多数服务商走 OpenAI 兼容协议,选预设即可。" +
                    "本地 Ollama 可不填 Key。</small></html>")
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return root!!
    }

    override fun isModified(): Boolean {
        val s = ThreadmapSettings.getInstance()
        return providerCombo.selectedItem != s.provider ||
            baseUrlField.text != s.baseUrl ||
            modelField.text != s.model ||
            String(keyField.password) != s.apiKey
    }

    override fun apply() {
        val s = ThreadmapSettings.getInstance()
        s.provider = providerCombo.selectedItem as? String ?: ThreadmapSettings.DEFAULT_PROVIDER
        s.baseUrl = baseUrlField.text.trim()
        s.model = modelField.text.trim()
        s.apiKey = String(keyField.password)
    }

    override fun reset() {
        val s = ThreadmapSettings.getInstance()
        loading = true
        providerCombo.selectedItem = s.provider
        baseUrlField.text = s.baseUrl
        modelField.text = s.model
        keyField.text = s.apiKey
        loading = false
    }
}
