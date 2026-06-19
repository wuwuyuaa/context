package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.threadmap.intellij.model.CallGraph
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * 用 JCEF 内嵌 Chromium 渲染调用图。第三方 JS 库 + graph.js 内联进 HTML（离线）。
 * JS 的点击/双击经 JBCefJSQuery 回传：onSelect（签名）/ onOpen（签名）。
 */
class GraphPanel(parent: Disposable) : JPanel(BorderLayout()) {

    private val browser = JBCefBrowser()
    private val selectQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val openQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val mapper = ObjectMapper()

    var onSelect: (String) -> Unit = {}
    var onOpen: (String) -> Unit = {}

    init {
        Disposer.register(parent, browser)
        selectQuery.addHandler { sig ->
            ApplicationManager.getApplication().invokeLater { onSelect(sig) }
            null
        }
        openQuery.addHandler { sig ->
            ApplicationManager.getApplication().invokeLater { onOpen(sig) }
            null
        }
        add(browser.component, BorderLayout.CENTER)
    }

    fun render(graph: CallGraph) {
        browser.loadHTML(buildHtml(graph))
    }

    private fun buildHtml(graph: CallGraph): String {
        val json = CallGraphJson.toJson(graph, mapper)
        val cytoscape = res("/web/cytoscape.min.js")
        val dagre = res("/web/dagre.min.js")
        val cytoscapeDagre = res("/web/cytoscape-dagre.js")
        val nodeHtmlLabel = res("/web/cytoscape-node-html-label.js")
        val graphJs = res("/web/graph.js")
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8"><style>
            html,body{margin:0;height:100%;background:#1e1f22;}
            #cy{width:100%;height:100vh;}
            .card{font-family:-apple-system,Segoe UI,sans-serif;color:#dfe1e5;background:#2b2d30;
                  border-radius:6px;padding:5px 8px;width:222px;box-sizing:border-box;overflow:hidden;}
            .title{font-family:ui-monospace,Menlo,monospace;font-weight:600;font-size:12px;
                   white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
            .summary{color:#9aa0a6;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
            .pills{margin-top:3px;}
            .pill{display:inline-block;font-size:10px;padding:0 5px;margin-right:3px;border-radius:3px;
                  background:#3a2c2e;border:1px solid #7c4448;color:#dfe1e5;}
            .star{color:#e5b454;}
            </style></head><body>
            <div id="cy"></div>
            <script>$cytoscape</script>
            <script>$dagre</script>
            <script>$cytoscapeDagre</script>
            <script>$nodeHtmlLabel</script>
            <script>
              window.__GRAPH__ = $json;
              window.threadmapSelect = function(sig){ ${selectQuery.inject("sig")} };
              window.threadmapOpen = function(sig){ ${openQuery.inject("sig")} };
            </script>
            <script>$graphJs</script>
            </body></html>
        """.trimIndent()
    }

    private fun res(path: String): String =
        javaClass.getResourceAsStream(path)?.readBytes()?.toString(Charsets.UTF_8)
            ?: error("缺少打包资源: $path")
}
